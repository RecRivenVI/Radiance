import unittest

from query_continuation_fixture import generate


class QueryContinuationFixtureTest(unittest.TestCase):
    def test_uses_complete_production_blocks_without_rewriting_their_control_flow(self):
        init = "bool initTransparent() { if (a) { b(); } return c; }"
        split = "if (useTransparentSplit) { MainRay source = mainRay; trace(source); }"
        output = generate("before\n" + init + "\nelsewhere\n" + split + "\nafter", "// PRODUCTION_INIT\n// PRODUCTION_SPLIT")
        self.assertEqual(init + "\n" + split, output)

    def test_missing_production_entry_fails_instead_of_testing_a_stale_copy(self):
        with self.assertRaises(ValueError):
            generate("void other() {}", "// PRODUCTION_INIT\n// PRODUCTION_SPLIT")


if __name__ == "__main__":
    unittest.main()
