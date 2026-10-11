#!/usr/bin/env python3
"""Explicit Modrinth release commands; no credentials needed before publish."""
import argparse
import getpass
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import stat
import subprocess
import sys
import urllib.error
import urllib.request
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PROJECT = 'fTSZ42IY'
SOURCE = 'https://github.com/Lab-11-org/mods-unified'
API = 'https://api.modrinth.com/v2'
TARGETS = {'forge': '1.20.1', 'neoforge': '1.21.1'}
CASES = ('ice_upgrade_recipes', 'ice_upgrade_transfer', 'core_cooking', 'restart_persistence')
TOKEN_FILE = ROOT / '.secrets/modrinth.token'


def require(condition, message):
    if not condition:
        raise ValueError(message)


INTERNAL_CHANGELOG_PATTERNS = (
    re.compile(r'pending\s+packaged[- ]?jar', re.I),
    re.compile(r'restart\s+verification', re.I),
    re.compile(r'must\s+not\s+be\s+overwritten', re.I),
    re.compile(r'do\s+not\s+overwrite', re.I),
    re.compile(r'release\s+is\s+pending', re.I),
    re.compile(r'e2e\s+json', re.I),
    re.compile(r'preflight', re.I),
    re.compile(r'manual[_ ]acceptance', re.I),
    re.compile(r'unpublished[_ ]candidate', re.I),
    re.compile(r'working\s+tree', re.I),
)


def assert_player_facing_changelog(notes):
    """Public release notes must only describe player-visible changes."""
    require(notes.strip(), 'Changelog is empty.')
    hits = [pat.pattern for pat in INTERNAL_CHANGELOG_PATTERNS if pat.search(notes)]
    require(not hits, 'Changelog looks internal/process-oriented; keep only player-facing changes. Matched: ' + ', '.join(hits))



def digest(path):
    return hashlib.sha512(Path(path).read_bytes()).hexdigest()


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT, text=True).strip()


def clean_commit():
    require(not git('status', '--porcelain'), 'Commit/review all release changes first; working tree is dirty.')
    return git('rev-parse', 'HEAD')


def write_json(path, value):
    Path(path).write_text(json.dumps(value, indent=2) + '\n')


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def api(route, body=None, content_type=None, token=None):
    headers = {'User-Agent': 'Lab-11-org/mods-unified-release (github.com/Lab-11-org/mods-unified)'}
    if token:
        headers['Authorization'] = token
    if content_type:
        headers['Content-Type'] = content_type
    request = urllib.request.Request(API + route, data=body, headers=headers)
    try:
        with urllib.request.build_opener(NoRedirect()).open(request, timeout=120) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        # Never print response bodies, headers, or the request object.
        raise ValueError(f'Modrinth HTTP {error.code}; inspect remote versions before retrying.') from None
    except (urllib.error.URLError, TimeoutError):
        raise ValueError('Modrinth network error; outcome may be uncertain. Rerun preflight before retrying.') from None


def remote_versions():
    project = api('/project/' + PROJECT)
    require(project['source_url'].rstrip('/') == SOURCE, 'Project source URL does not match this repository.')
    return api('/project/' + PROJECT + '/version')


def matching_version(versions, version, artifact):
    matches = [v for v in versions if v['version_number'] == version
               and artifact['loader'] in v['loaders']]
    require(len(matches) <= 1, 'Ambiguous duplicate remote versions; inspect manually.')
    if not matches:
        return None
    found = matches[0]
    require(found.get('status', 'listed') == 'listed', 'Existing version is not publicly listed.')
    require(found['game_versions'] == [artifact['minecraft']], 'Existing version has different Minecraft metadata.')
    require(len(found['files']) == 1 and found['files'][0]['hashes']['sha512'] == artifact['sha512'],
            'Version already exists with different bytes; use a new version. Never overwrite.')
    return found


def verify_public(version, artifact):
    public = api('/version/' + version['id'])
    require(public['project_id'] == PROJECT, 'Published version belongs to a different project.')
    require(public['loaders'] == [artifact['loader']] and public['game_versions'] == [artifact['minecraft']],
            'Published loader/Minecraft metadata mismatch.')
    require(public.get('status') == 'listed', 'Published version is not listed.')
    file = next((f for f in public['files'] if f['hashes']['sha512'] == artifact['sha512']), None)
    require(file is not None, 'Published file hash mismatch.')
    require(file['url'].startswith('https://cdn.modrinth.com/'), 'Unexpected file download host.')
    with urllib.request.urlopen(file['url'], timeout=120) as response:
        require(hashlib.sha512(response.read()).hexdigest() == artifact['sha512'], 'Downloaded file hash mismatch.')
    return 'https://modrinth.com/mod/codes-mod-unified/version/' + public['id']


def validate_e2e(report, manifest, directory):
    require(report.get('commit') == manifest['commit'], 'E2E commit does not match build commit.')
    entries = report.get('artifacts', [])
    for artifact in manifest['artifacts']:
        entries_for_hash = [e for e in entries if e.get('sha512') == artifact['sha512']]
        require(len(entries_for_hash) == 1, 'E2E report missing/duplicated for exact JAR SHA-512.')
        entry = entries_for_hash[0]
        require(entry.get('loader') == artifact['loader'] and entry.get('minecraft') == artifact['minecraft'],
                'E2E loader/Minecraft mismatch.')
        require(entry.get('tested_packaged_jar') is True, 'E2E must load the packaged JAR, not development classes.')
        require(entry.get('runtime_versions') and entry.get('tester') and entry.get('tested_at'),
                'Record runtime versions, tester and test timestamp.')
        required = list(CASES) + (['central_kitchen_basket'] if artifact['loader'] == 'forge' else [])
        for case in required:
            result = entry.get('cases', {}).get(case, {})
            require(result.get('status') == 'passed', f'E2E case not passed: {artifact["loader"]}/{case}')
            require(result.get('observations') and result.get('evidence'), f'Missing E2E observations/evidence: {case}')
            for name in result['evidence']:
                path = (directory / name).resolve()
                require(path.is_relative_to(directory.resolve()) and path.is_file() and path.stat().st_size > 0,
                        f'Missing local E2E evidence file: {name}')


def load_bundle(directory):
    manifest = json.loads((directory / 'manifest.json').read_text())
    require(manifest['project_id'] == PROJECT, 'Wrong Modrinth project.')
    require(manifest['commit'] == clean_commit(), 'Current source commit differs from build manifest.')
    require({a['loader'] for a in manifest['artifacts']} == set(TARGETS)
            and len(manifest['artifacts']) == 2, 'Bundle must contain one artifact for each supported loader.')
    for artifact in manifest['artifacts']:
        require(artifact['minecraft'] == TARGETS[artifact['loader']], 'Unsupported Minecraft version.')
        path = directory / artifact['filename']
        require(path.resolve().parent == directory.resolve(), 'Artifact must be directly inside bundle.')
        require(digest(path) == artifact['sha512'], 'JAR changed after prepare; rebuild and repeat E2E.')
        metadata = 'META-INF/' + ('neoforge.mods.toml' if artifact['loader'] == 'neoforge' else 'mods.toml')
        import tomllib
        with zipfile.ZipFile(path) as jar:
            mods = tomllib.loads(jar.read(metadata).decode())['mods']
            require(any(m.get('modId') == 'lab_11_mods_unified' and m.get('version') == manifest['version'] for m in mods),
                    'JAR metadata version/mod ID mismatch.')
    require(digest(directory / 'changelog.md') == manifest['changelog_sha512'], 'Changelog changed after prepare.')
    validate_e2e(json.loads((directory / 'e2e.json').read_text()), manifest, directory)
    return manifest


def restore_workdir_jars(directory, artifacts):
    """Keep prepared JARs under build/libs even after gradle clean steps wipe that folder."""
    libs = ROOT / 'build' / 'libs'
    libs.mkdir(parents=True, exist_ok=True)
    for artifact in artifacts:
        shutil.copy2(directory / artifact['filename'], libs / artifact['filename'])

def prepare(directory, changelog):
    commit = clean_commit()
    version = re.search(r'^mod_version=(.+)$', (ROOT / 'gradle.properties').read_text(), re.M)[1].strip()
    require(re.fullmatch(r'\d+\.\d+\.\d+(?:[-+][A-Za-z0-9.-]+)?', version), 'Invalid mod_version.')
    versions = remote_versions()
    require(not any(v['version_number'] == version for v in versions), 'Version already published; choose a new version first.')
    require(not directory.exists(), 'Output directory already exists; choose a fresh bundle directory.')
    notes = changelog.read_text()
    assert_player_facing_changelog(notes)
    directory.mkdir(parents=True)
    (directory / 'changelog.md').write_text(notes)
    artifacts = []
    for loader, minecraft in TARGETS.items():
        args = [f'-Ptarget_id={loader}-{minecraft}', '-Pfull_runtime=true']
        for task in (['clean', 'build'], ['runGameTestServer']):
            name = loader + '-' + task[-1] + '.log'
            with (directory / name).open('w') as log:
                subprocess.run(['./gradlew', *task, *args, '--console=plain'], cwd=ROOT,
                               stdout=log, stderr=subprocess.STDOUT, check=True)
        filename = f'lab_11_mods_unified-{loader}-{minecraft}-{version}.jar'
        shutil.copy2(ROOT / 'build/libs' / filename, directory / filename)
        artifacts.append({'loader': loader, 'minecraft': minecraft, 'filename': filename,
                          'sha512': digest(directory / filename)})
    with (directory / 'neoforge-newer-dependencies.log').open('w') as log:
        subprocess.run(['./gradlew', 'clean', 'check', '-Pbalm_compile_version=21.0.64',
                        '-Pcfbh_compile_version=21.1.24', '-Pfarmers_delight_compile_version=1.3.2',
                        '--console=plain'], cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, check=True)
    with (directory / 'forge-central-kitchen-regression.log').open('w') as log:
        subprocess.run(['./gradlew', 'runGameTestServer', '-Ptarget_id=forge-1.20.1',
                        '-Pfull_runtime=true', '-Pcentral_kitchen_runtime=true', '-Pforge_version=47.4.20',
                        '--console=plain'], cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, check=True)
    require(clean_commit() == commit, 'Source changed during build; discard bundle.')
    manifest = {'project_id': PROJECT, 'version': version, 'commit': commit, 'artifacts': artifacts,
                'changelog_sha512': digest(directory / 'changelog.md')}
    write_json(directory / 'manifest.json', manifest)
    template = {'commit': commit, 'artifacts': []}
    for artifact in artifacts:
        cases = list(CASES) + (['central_kitchen_basket'] if artifact['loader'] == 'forge' else [])
        template['artifacts'].append({**artifact, 'tested_packaged_jar': False, 'tester': '', 'tested_at': '',
                                     'runtime_versions': {}, 'cases': {c: {'status': 'not_run',
                                     'observations': '', 'evidence': []} for c in cases}})
    write_json(directory / 'e2e.json', template)
    restore_workdir_jars(directory, artifacts)
    print(f'Prepared {directory}; JARs also kept under build/libs. Run packaged-JAR E2E and complete e2e.json before preflight.')


def token_value():
    token = os.environ.get('MODRINTH_TOKEN')
    if not token and TOKEN_FILE.exists():
        info = TOKEN_FILE.lstat()
        require(stat.S_ISREG(info.st_mode) and info.st_uid == os.getuid() and not info.st_mode & 0o077,
                'Token file must be a regular file owned by you with mode 600.')
        token = TOKEN_FILE.read_text().strip()
    if not token and sys.stdin.isatty():
        token = getpass.getpass('Modrinth token (hidden, not saved): ').strip()
    require(token and '\n' not in token and '\r' not in token, 'Set MODRINTH_TOKEN or supply token securely; do not pass it as an argument.')
    return token


def multipart(metadata, filename, data):
    boundary = 'mods-unified-' + uuid.uuid4().hex
    prefix = (f'--{boundary}\r\nContent-Disposition: form-data; name="data"\r\n'
              'Content-Type: application/json\r\n\r\n' + json.dumps(metadata) + '\r\n'
              f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\n'
              'Content-Type: application/java-archive\r\n\r\n').encode()
    return prefix + data + f'\r\n--{boundary}--\r\n'.encode(), 'multipart/form-data; boundary=' + boundary


def release(directory, publish):
    manifest = load_bundle(directory)
    assert_player_facing_changelog((directory / 'changelog.md').read_text())
    versions = remote_versions()
    pending = []
    for artifact in manifest['artifacts']:
        existing = matching_version(versions, manifest['version'], artifact)
        pending.append((artifact, existing))
        print(artifact['filename'] + (': already published, same SHA-512' if existing else ': ready for upload'))
    if not publish:
        print('Preflight passed; no credentials read and no uploads performed.')
        return
    token = token_value() if any(v is None for _, v in pending) else None
    receipts = []
    for artifact, existing in pending:
        if existing is None:
            # Recheck immediately before each non-idempotent POST. Never retry a POST automatically.
            existing = matching_version(remote_versions(), manifest['version'], artifact)
        if existing is None:
            data = (directory / artifact['filename']).read_bytes()
            require(hashlib.sha512(data).hexdigest() == artifact['sha512'], 'Artifact changed before upload.')
            metadata = {'project_id': PROJECT, 'name': f'{manifest["version"]} ({artifact["loader"]} {artifact["minecraft"]})',
                        'version_number': manifest['version'], 'changelog': (directory / 'changelog.md').read_text(),
                        'dependencies': [], 'game_versions': [artifact['minecraft']], 'loaders': [artifact['loader']],
                        'version_type': ('beta' if any(x in manifest['version'].lower() for x in ('-beta', '-alpha', '-rc')) else 'release'), 'featured': False, 'status': 'listed',
                        'file_parts': ['file'], 'primary_file': 'file'}
            body, content_type = multipart(metadata, artifact['filename'], data)
            existing = api('/version', body, content_type, token)
            # Persist the returned ID before any subsequent verification request can fail.
            write_json(directory / (artifact['loader'] + '-receipt.json'),
                       {'id': existing['id'], 'sha512': artifact['sha512'], 'verification': 'pending'})
        url = verify_public(existing, artifact)
        receipt = {'id': existing['id'], 'sha512': artifact['sha512'], 'url': url, 'verification': 'passed'}
        write_json(directory / (artifact['loader'] + '-receipt.json'), receipt)
        receipts.append(receipt)
        print(url)
    write_json(directory / 'published.json', receipts)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['prepare', 'preflight', 'publish'])
    parser.add_argument('--bundle', type=Path, required=True)
    parser.add_argument('--changelog', type=Path)
    args = parser.parse_args()
    if args.command == 'prepare':
        require(args.changelog is not None, 'prepare requires --changelog')
        prepare(args.bundle.resolve(), args.changelog)
    else:
        release(args.bundle.resolve(), args.command == 'publish')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, KeyError, OSError, subprocess.CalledProcessError, zipfile.BadZipFile) as error:
        print(f'Release blocked: {error}', file=sys.stderr)
        sys.exit(1)
