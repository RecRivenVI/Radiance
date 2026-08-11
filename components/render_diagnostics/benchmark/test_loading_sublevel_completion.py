import unittest

from sublevel_investigation import validate_completion


class SublevelCompletionTest(unittest.TestCase):
    def snapshot(self, ready=422, dirty=0, registered=665):
        return [{'handoffs': [1,1,0,1], 'clientSublevels': [dict(uuid='large', sections=665,
                                         registered=registered, dirty=dirty, ready=ready)]}]

    def test_completed_source_distinguishes_empty_sections_from_missing_geometry(self):
        result = validate_completion(self.snapshot(), 422, 'large')
        self.assertTrue(result['passed'])
        self.assertEqual(result['emptyWithoutBlas'], 243)

    def test_normal_exit_or_a_clear_queue_cannot_prove_publication(self):
        with self.assertRaises(RuntimeError):
            validate_completion(self.snapshot(ready=0), 422)
        with self.assertRaises(RuntimeError):
            validate_completion(self.snapshot(ready=421), 422)

    def test_dirty_unregistered_or_wrong_source_is_not_complete(self):
        for rows in (self.snapshot(dirty=1), self.snapshot(registered=664)):
            with self.assertRaises(RuntimeError):
                validate_completion(rows, 422, 'large')
        with self.assertRaises(RuntimeError):
            validate_completion(self.snapshot(), 422, 'other')

    def test_all_empty_source_needs_no_blas(self):
        rows = [{'handoffs': [1,1,0,1], 'clientSublevels': [dict(uuid='empty', sections=3, registered=3, dirty=0, ready=0)]}]
        self.assertEqual(validate_completion(rows, 0, 'empty')['emptyWithoutBlas'], 3)

    def test_missing_snapshot_is_not_success(self):
        with self.assertRaises(RuntimeError):
            validate_completion([], 422)

    def test_old_visible_geometry_does_not_prove_the_replacement_was_published(self):
        rows = self.snapshot()
        rows[0]['handoffs'] = [2,1,1,1]
        with self.assertRaises(RuntimeError):
            validate_completion(rows, 422, 'large')
        del rows[0]['handoffs']
        with self.assertRaises(RuntimeError):
            validate_completion(rows, 422, 'large')


if __name__ == '__main__':
    unittest.main()
