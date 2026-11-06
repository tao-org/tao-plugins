"""Run after WorkflowScriptExecutorTest, under Linux/WSL; Docker is replaced by a recording shim."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

FIXTURES = Path(__file__).resolve().parents[3] / 'target/workflow-script-fixtures'


class WorkflowScriptsTest(unittest.TestCase):
    def run_script(self, scenario, ext):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            docker = root / 'docker'
            docker.write_text("#!/usr/bin/env python3\nimport json, os, sys\n"
                              "with open(os.environ['TRACE'], 'a') as f: f.write(json.dumps(sys.argv[1:]) + '\\n')\n"
                              "sys.exit(7 if 'FAIL' in sys.argv else 0)\n")
            docker.chmod(0o755)
            trace = root / 'trace'
            env = dict(os.environ, PATH=str(root) + ':' + os.environ['PATH'], TRACE=str(trace))
            result = subprocess.run(['python3' if ext == '.py' else 'bash', str(FIXTURES / (scenario + ext))],
                                    env=env, text=True, capture_output=True, timeout=10)
            calls = [json.loads(line) for line in trace.read_text().splitlines()] if trace.exists() else []
            return result, calls

    def test_dependency_order_and_exact_arguments(self):
        for ext in ('.sh', '.py'):
            with self.subTest(ext=ext):
                result, calls = self.run_script('ordered', ext)
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertEqual(2, len(calls))
                self.assertEqual('parent', calls[0][-1])
                self.assertEqual(['/bin/bash', '-c', 'printf \'%s\' "$1"', '--',
                                  "a 'quoted' $literal\nline", '', 'λ'], calls[1][-7:])
                self.assertIn('/host space:/data', calls[1])
                self.assertIn('VALUE=quote\'" $(touch injected) `false`\nline', calls[1])

    def test_stops_after_failure(self):
        for ext in ('.sh', '.py'):
            with self.subTest(ext=ext):
                result, calls = self.run_script('failure', ext)
                self.assertNotEqual(0, result.returncode)
                self.assertEqual(1, len(calls))
                self.assertEqual('FAIL', calls[0][-1])

    def test_branches_and_join(self):
        for ext in ('.sh', '.py'):
            with self.subTest(ext=ext):
                result, calls = self.run_script('diamond', ext)
                self.assertEqual(0, result.returncode, result.stderr)
                order = [call[-1] for call in calls]
                self.assertEqual('1', order[0])
                self.assertEqual('4', order[-1])
                self.assertEqual({'2', '3'}, set(order[1:3]))

    def test_invalid_graphs_fail(self):
        for ext in ('.sh', '.py'):
            for scenario in ('missing', 'cycle', 'duplicate'):
                with self.subTest(ext=ext, scenario=scenario):
                    result, calls = self.run_script(scenario, ext)
                    self.assertNotEqual(0, result.returncode)
                    self.assertEqual([], calls)

    def test_native_shell_payload_and_working_directory(self):
        for ext in ('.sh', '.py'):
            with self.subTest(ext=ext):
                result, calls = self.run_script('native', ext)
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertEqual("a 'quoted' $literal\nsecond line\n/tmp\n", result.stdout)
                self.assertEqual([], calls)


if __name__ == '__main__':
    unittest.main()
