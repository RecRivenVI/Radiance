"""Reuse the small functional fixture; never copy a user instance or measure performance."""
from prepare_takeover_validation import main

if __name__ == '__main__':
    main('conformance-boundaries')
