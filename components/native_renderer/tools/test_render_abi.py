import unittest
from generate_java_abi import parse, java


class RenderAbiGeneratorTest(unittest.TestCase):
    def test_exact_values_and_order(self):
        values = parse("RENDER_ABI(FIRST, 0u)\nRENDER_ABI(SECOND, 0x1fu)\n")
        self.assertEqual([("FIRST", 0), ("SECOND", 31)], values)
        self.assertIn("public static final int SECOND = 31;", java(values))
        self.assertLess(java(values).index("FIRST,"), java(values).index("SECOND,"))

    def test_duplicate_or_bad_declarations_are_rejected(self):
        for text in ["", "RENDER_ABI(A, 1u)\nRENDER_ABI(A, 2u)",
                     "RENDER_ABI(A, 0x100000000u)", "RENDER_ABI(A, 1 << 3)",
                     "RENDER_ABI(A, -1u)", "arbitrary code"]:
            with self.subTest(text=text), self.assertRaises(ValueError):
                parse(text)

    def test_unsigned_wire_values_keep_all_bits_in_java(self):
        self.assertIn("= -1;", java(parse("RENDER_ABI(MASK, 0xffffffffu)")))

    def test_comments_do_not_change_output(self):
        self.assertEqual(java(parse("RENDER_ABI(A, 1u)")),
                         java(parse("// comment\n\nRENDER_ABI(A, 1u)\n")))


if __name__ == "__main__":
    unittest.main()
