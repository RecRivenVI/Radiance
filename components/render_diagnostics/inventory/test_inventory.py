"""Tests for analysis tools only; never imports game classes or calls product APIs."""
import hashlib, tempfile, unittest, zipfile
from pathlib import Path
from acquire_references import extract_text, sha
from static_inventory import matches, contextual_consumers, write

class InventoryToolsTest(unittest.TestCase):
    def test_raw_hash_keeps_line_endings(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/"x.java"; p.write_bytes(b"x\r\n")
            self.assertEqual(sha(p),hashlib.sha256(b"x\r\n").hexdigest())
            self.assertNotEqual(sha(p),hashlib.sha256(b"x\n").hexdigest())
    def test_extraction_rejects_parent_traversal(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/"x.zip"
            with zipfile.ZipFile(p,"w") as z:z.writestr("../escape.java","x")
            with self.assertRaises(ValueError):extract_text(p,Path(d)/"output")
            self.assertFalse((Path(d)/"escape.java").exists())
    def test_existing_differing_reference_is_preserved(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/"x.zip"; out=Path(d)/"out"; out.mkdir(); (out/"a.java").write_text("old")
            with zipfile.ZipFile(p,"w") as z:z.writestr("a.java","new")
            with self.assertRaises(ValueError):extract_text(p,out)
            self.assertEqual((out/"a.java").read_text(),"old")
    def test_extraction_rejects_windows_separator_traversal(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/"x.zip"
            with zipfile.ZipFile(p,"w") as z:z.writestr("..\\escape.java","x")
            with self.assertRaises(ValueError):extract_text(p,Path(d)/"output")
            self.assertFalse((Path(d)/"escape.java").exists())
    def test_extraction_rejects_windows_drive_path(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/"x.zip"
            with zipfile.ZipFile(p,"w") as z:z.writestr("C:/escape.java","x")
            with self.assertRaises(ValueError):extract_text(p,Path(d)/"output")
    def test_candidate_is_explicitly_lexical(self):
        r=matches({"a":"// glDrawArrays()\nglDrawArrays();"},r"glDrawArrays")
        self.assertEqual(r,[dict(path="a",line=2,text="glDrawArrays();",evidence="static_lexical_lead")])
    def test_consumer_token_boundary(self):
        r=contextual_consumers({"a":"MAX_X\nMAX_XYZ\n// MAX_X"},"MAX_X","none")
        self.assertEqual(len(r),1)
    def test_json_csv_repeatable_and_quoted(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d); rows=[dict(path="x",text='comma, quote "',status="unknown")]
            write(p,"a",rows); write(p,"b",rows)
            self.assertEqual((p/"a.json").read_bytes(),(p/"b.json").read_bytes())
            self.assertEqual((p/"a.csv").read_bytes(),(p/"b.csv").read_bytes())

if __name__=="__main__":unittest.main()
