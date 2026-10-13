# Tests for the dev/test split (golden/split.py). They define "done" for exercise E3.
from collections import Counter, defaultdict

from golden.split import SplitItem, assign_splits


# HELPER: fake items with the same shape as the real catalog: (scenario, number of groups, invoices per group).
def dataset_like() -> list[SplitItem]:
    """Same shape as the real catalog: (scenario, groups, invoices per group)."""
    shape = [("clean_full", 50, 1), ("clean_partial_delivery", 10, 1), ("price_at_tolerance", 12, 1),
             ("partial_series_overbilling", 4, 3), ("price_above_tolerance", 18, 1), ("quantity_not_received", 14, 1),
             ("tax_rate_wrong", 14, 1), ("unmatched_freight", 14, 1), ("duplicate_pair", 6, 2),
             ("currency_mismatch", 10, 1), ("missing_po", 10, 1), ("price_and_tax", 6, 1),
             ("missing_po_duplicate", 3, 2), ("freight_and_price", 6, 1), ("currency_and_quantity", 6, 1)]
    items, n, g = [], 0, 0
    for scenario, groups, size in shape:
        for _ in range(groups):  # _ = "loop variable not needed" (Kotlin: repeat(groups) { })
            g += 1
            for _ in range(size):
                n += 1
                items.append(SplitItem(f"inv-{n:04d}", f"g-{g:03d}", scenario))  # inv-0001, g-001
    return items


ITEMS = dataset_like()  # 200 items in 183 groups, like the real dataset


# TEST: every invoice gets a split, and both splits are used.
def test_every_item_gets_dev_or_test():
    splits = assign_splits(ITEMS, 50, 42)
    assert set(splits) == {i.id for i in ITEMS}  # set(dict) = its keys
    assert set(splits.values()) == {"dev", "test"}


# TEST: all invoices of one group are in the same split (no leakage).
def test_groups_stay_together():
    splits = assign_splits(ITEMS, 50, 42)
    per_group = defaultdict(set)
    for item in ITEMS:
        per_group[item.group].add(splits[item.id])
    assert all(len(s) == 1 for s in per_group.values())  # one split per group, never both


# TEST: about 50 invoices in dev (whole groups make an exact 50 impossible).
def test_dev_size_is_close_to_target():
    dev = sum(1 for s in assign_splits(ITEMS, 50, 42).values() if s == "dev")
    assert 45 <= dev <= 55


# TEST: stratified — every scenario has at least one invoice in dev AND in test.
def test_stratified_every_scenario_in_both_splits():
    splits = assign_splits(ITEMS, 50, 42)
    seen = defaultdict(Counter)  # scenario -> {"dev": n, "test": m}
    for item in ITEMS:
        seen[item.scenario][splits[item.id]] += 1
    for scenario, counts in seen.items():
        assert counts["dev"] >= 1 and counts["test"] >= 1, scenario


# TEST: same seed -> same split; different seed -> different split.
def test_deterministic_per_seed():
    assert assign_splits(ITEMS, 50, 42) == assign_splits(ITEMS, 50, 42)
    assert assign_splits(ITEMS, 50, 42) != assign_splits(ITEMS, 50, 7)
