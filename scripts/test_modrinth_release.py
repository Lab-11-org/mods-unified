import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('release', Path(__file__).with_name('modrinth_release.py'))
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


class ReleaseGateTest(unittest.TestCase):
    def setUp(self):
        self.artifact = {'loader': 'forge', 'minecraft': '1.20.1', 'sha512': 'abc', 'filename': 'test.jar'}
        self.manifest = {'commit': 'commit', 'version': '0.2.3', 'artifacts': [self.artifact]}
        self.version = {'id': 'id', 'version_number': '0.2.3', 'status': 'listed', 'loaders': ['forge'],
                        'game_versions': ['1.20.1'], 'files': [{'hashes': {'sha512': 'abc'}}]}

    def test_token_path_is_repository_local(self):
        self.assertEqual(m.TOKEN_FILE, m.ROOT / '.secrets/modrinth.token')

    def test_environment_token_does_not_read_file(self):
        with patch.dict(m.os.environ, {'MODRINTH_TOKEN': 'test-only-placeholder'}), \
             patch.object(m, 'TOKEN_FILE') as token_file:
            self.assertEqual(m.token_value(), 'test-only-placeholder')
            token_file.exists.assert_not_called()
            token_file.read_text.assert_not_called()

    def test_symlink_token_file_is_rejected_without_reading(self):
        with patch.dict(m.os.environ, {}, clear=True), patch.object(m, 'TOKEN_FILE') as token_file:
            token_file.exists.return_value = True
            token_file.lstat.return_value.st_mode = m.stat.S_IFLNK | 0o600
            with self.assertRaisesRegex(ValueError, 'regular file'):
                m.token_value()
            token_file.read_text.assert_not_called()

    def test_existing_different_bytes_blocked(self):
        self.version['files'][0]['hashes']['sha512'] = 'different'
        with self.assertRaisesRegex(ValueError, 'different bytes'):
            m.matching_version([self.version], '0.2.3', self.artifact)

    def test_existing_same_bytes_can_resume(self):
        self.assertEqual(m.matching_version([self.version], '0.2.3', self.artifact)['id'], 'id')

    def test_loader_specific_version(self):
        self.version['loaders'] = ['neoforge']
        self.assertIsNone(m.matching_version([self.version], '0.2.3', self.artifact))

    def test_e2e_missing_basket_or_evidence_blocks(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / 'test.log').write_text('observed real test')
            cases = {c: {'status': 'passed', 'observations': 'observed', 'evidence': ['test.log']}
                     for c in (*m.CASES, 'central_kitchen_basket')}
            entry = {**self.artifact, 'tested_packaged_jar': True, 'runtime_versions': {'forge': '47.4.20'},
                     'tester': 'tester', 'tested_at': '2026-10-09', 'cases': cases}
            report = {'commit': 'commit', 'artifacts': [entry]}
            m.validate_e2e(report, self.manifest, root)
            cases['central_kitchen_basket']['status'] = 'not_run'
            with self.assertRaisesRegex(ValueError, 'not passed'):
                m.validate_e2e(report, self.manifest, root)
            cases['central_kitchen_basket']['status'] = 'passed'
            cases['central_kitchen_basket']['evidence'] = ['missing.log']
            with self.assertRaisesRegex(ValueError, 'Missing local E2E evidence'):
                m.validate_e2e(report, self.manifest, root)

    def test_development_classes_do_not_count_as_e2e(self):
        entry = {**self.artifact, 'tested_packaged_jar': False}
        with self.assertRaisesRegex(ValueError, 'packaged JAR'):
            m.validate_e2e({'commit': 'commit', 'artifacts': [entry]}, self.manifest, Path('/tmp'))

    def test_preflight_never_reads_token_or_posts(self):
        with tempfile.TemporaryDirectory() as tmp, \
             patch.object(m, 'load_bundle', return_value=self.manifest), \
             patch.object(m, 'remote_versions', return_value=[]), \
             patch.object(m, 'token_value') as token, patch.object(m, 'api') as api:
            directory = Path(tmp)
            (directory / 'changelog.md').write_text('Fixed oven cookware placement.')
            m.release(directory, False)
            token.assert_not_called()
            api.assert_not_called()

    def test_failed_gate_never_reads_token_or_posts(self):
        with patch.object(m, 'load_bundle', side_effect=ValueError('E2E failed')), \
             patch.object(m, 'token_value') as token, patch.object(m, 'api') as api:
            with self.assertRaises(ValueError):
                m.release(Path('/tmp'), True)
            token.assert_not_called()
            api.assert_not_called()

    def test_multipart_has_declared_file_and_json(self):
        body, kind = m.multipart({'file_parts': ['file'], 'project_id': m.PROJECT}, 'a.jar', b'jar bytes')
        self.assertIn(b'name="data"', body)
        self.assertIn(b'name="file"; filename="a.jar"', body)
        self.assertIn(b'jar bytes', body)
        self.assertTrue(body.endswith(('--' + kind.split('boundary=')[1] + '--\r\n').encode()))



    def test_restore_workdir_jars_copies_into_build_libs(self):
        import tempfile
        from pathlib import Path
        with tempfile.TemporaryDirectory() as tmp:
            bundle = Path(tmp) / 'bundle'
            bundle.mkdir()
            jar = bundle / 'lab_11_mods_unified-forge-1.20.1-9.9.9.jar'
            jar.write_bytes(b'jar-bytes')
            artifacts = [{'filename': jar.name}]
            # Point ROOT temporarily by patching
            with patch.object(m, 'ROOT', Path(tmp) / 'repo'):
                (Path(tmp) / 'repo').mkdir()
                m.restore_workdir_jars(bundle, artifacts)
                kept = Path(tmp) / 'repo' / 'build' / 'libs' / jar.name
                self.assertTrue(kept.is_file())
                self.assertEqual(kept.read_bytes(), b'jar-bytes')

if __name__ == '__main__':
    unittest.main()
