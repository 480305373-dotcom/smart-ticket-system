import unittest
from capacity_benchmark import percentile, summarize


class CapacityReportTests(unittest.TestCase):
    def test_percentiles_use_nearest_rank_including_tail(self):
        self.assertEqual(percentile(list(range(1, 101)), 95), 95)
        self.assertEqual(percentile([100, 2, 3], 99), 100)

    def test_rate_limit_and_transport_errors_are_not_successful_throughput(self):
        result = summarize([(200, 10, "core-a"), (429, 1, "core-b"),
                            ("transport_error", 3000, "")], 2)
        self.assertEqual(result["successfulRequestsPerSecond"], 0.5)
        self.assertAlmostEqual(result["errorRate"], 2 / 3, places=5)
        self.assertEqual(result["successLatencyMs"]["p99"], 10)
        self.assertEqual(result["allLatencyMs"]["p95"], 3000)

    def test_empty_run_cannot_be_reported_as_passing(self):
        with self.assertRaises(ValueError):
            summarize([], 10)


if __name__ == "__main__":
    unittest.main()
