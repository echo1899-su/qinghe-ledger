import ast
import tempfile
import unittest
from pathlib import Path

source = Path(__file__).resolve().parents[1] / "build/build_qinghe.py"
tree = ast.parse(source.read_text(encoding="utf-8"))
pick_node = next(n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name == "pick")

class ToolchainTest(unittest.TestCase):
    def test_layouts_missing_and_ambiguous(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            scope = {"Path": Path, "TOOL": root}
            exec(compile(ast.Module(body=[pick_node], type_ignores=[]), str(source), "exec"), scope)
            pick = scope["pick"]
            with self.assertRaises(SystemExit): pick("platform")
            base = root / "platform"
            base.mkdir()
            with self.assertRaises(SystemExit): pick("platform")
            (base / "android.jar").touch()
            self.assertEqual(pick("platform"), base)
            (base / "android.jar").unlink()
            version = base / "android-35"
            version.mkdir()
            (version / "android.jar").touch()
            self.assertEqual(pick("platform"), version)
            (base / "android.jar").touch()
            with self.assertRaises(SystemExit): pick("platform")
