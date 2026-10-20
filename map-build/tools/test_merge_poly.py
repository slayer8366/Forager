"""Tests for merge_poly.py. Run: python3 -m unittest map-build/tools/test_merge_poly.py"""
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(__file__))
import merge_poly  # noqa: E402

US = """none
1
   -124.0   48.0
   -66.0   44.0
   -80.0   25.0
END
2
   172.0   52.0
   180.0   52.0
   180.0   53.5
END
END
"""
CANADA = """none
1
   -141.0   60.0
   -52.0   47.0
   -95.0   49.0
END
END
"""


# Stand-ins for us/puerto-rico.poly and us/us-virgin-islands.poly (dispatch 2026-10-20-pi-14). Made
# up, not Geofabrik's vertices: what they share with the real two is that they overlap slightly,
# each with one vertex inside the other, as the real outlines do (read 2026-10-20).
PUERTO_RICO = """none
1
   -68.0   17.5
   -65.0   17.5
   -65.0   18.5
   -68.0   18.5
END
END
"""
USVI = """none
1
   -65.2   17.0
   -64.0   17.0
   -64.0   18.5
   -65.2   18.5
END
END
"""


class MergePolyTest(unittest.TestCase):
    def test_four_outlines_two_overlapping_are_all_kept_in_order(self):
        # build-usca.sh passes the outlines in USCA_REGIONS order: us, canada, puerto-rico, usvi.
        out = merge_poly.merge("usca", [("us.poly", US), ("canada.poly", CANADA),
                                        ("puerto-rico.poly", PUERTO_RICO), ("us-virgin-islands.poly", USVI)])
        got = merge_poly.sections(out, "out")
        self.assertEqual([name for name, _ in got], ["usca-1-1", "usca-1-2", "usca-2-1", "usca-3-1", "usca-4-1"])
        self.assertEqual(got[3][1], [["-68.0", "17.5"], ["-65.0", "17.5"], ["-65.0", "18.5"], ["-68.0", "18.5"]])
        self.assertEqual(got[4][1], [["-65.2", "17.0"], ["-64.0", "17.0"], ["-64.0", "18.5"], ["-65.2", "18.5"]])
        # The overlap is passed through as two rings, not merged or dropped: Planetiler reads the
        # union of the sections, so a shared strip is simply inside both.
        self.assertFalse(any(name.startswith("!") for name, _ in got))

    def test_main_joins_four_files(self):
        with tempfile.TemporaryDirectory() as d:
            paths = []
            for n, text in (("us", US), ("canada", CANADA), ("puerto-rico", PUERTO_RICO), ("us-virgin-islands", USVI)):
                p = os.path.join(d, n + ".poly")
                open(p, "w").write(text)
                paths.append(p)
            out = os.path.join(d, "out.poly")
            self.assertEqual(merge_poly.main(["forager-usca", out] + paths), 0)
            self.assertEqual([n for n, _ in merge_poly.sections(open(out).read(), out)],
                             ["forager-usca-1-1", "forager-usca-1-2", "forager-usca-2-1",
                              "forager-usca-3-1", "forager-usca-4-1"])


    def test_every_section_of_every_input_is_kept_in_order(self):
        out = merge_poly.merge("usca", [("us.poly", US), ("canada.poly", CANADA)])
        got = merge_poly.sections(out, "out")
        self.assertEqual([name for name, _ in got], ["usca-1-1", "usca-1-2", "usca-2-1"])
        self.assertEqual(got[1][1], [["172.0", "52.0"], ["180.0", "52.0"], ["180.0", "53.5"]])
        self.assertEqual(got[2][1][1], ["-52.0", "47.0"])
        self.assertTrue(out.startswith("usca\n") and out.endswith("END\nEND\n"))

    def test_a_hole_stays_a_hole(self):
        holed = "x\n1\n 0 0\n 1 0\n 1 1\nEND\n!2\n 0.2 0.2\n 0.4 0.2\n 0.4 0.4\nEND\nEND\n"
        names = [n for n, _ in merge_poly.sections(merge_poly.merge("m", [("h", holed)]), "out")]
        self.assertEqual(names, ["m-1-1", "!m-1-2"])

    def test_a_truncated_input_is_refused(self):
        with self.assertRaisesRegex(ValueError, "does not end with END"):
            merge_poly.merge("usca", [("us.poly", US), ("canada.poly", CANADA[:40])])

    def test_bad_coordinates_are_refused(self):
        with self.assertRaisesRegex(ValueError, "out of range"):
            merge_poly.sections("x\n1\n 190 0\n 1 0\n 1 1\nEND\nEND\n", "bad")
        with self.assertRaisesRegex(ValueError, "two coordinates"):
            merge_poly.sections("x\n1\n 1 0 3\n 1 0\n 1 1\nEND\nEND\n", "bad")

    def test_main_writes_nothing_when_an_input_is_bad(self):
        with tempfile.TemporaryDirectory() as d:
            good, bad, out = (os.path.join(d, n) for n in ("us.poly", "ca.poly", "out.poly"))
            open(good, "w").write(US)
            open(bad, "w").write("none\n1\n")
            self.assertEqual(merge_poly.main(["usca", out, good, bad]), 1)
            self.assertFalse(os.path.exists(out) or os.path.exists(out + ".part"))
            self.assertEqual(merge_poly.main(["usca", out, good]), 0)
            self.assertEqual(len(merge_poly.sections(open(out).read(), out)), 2)


if __name__ == "__main__":
    unittest.main()
