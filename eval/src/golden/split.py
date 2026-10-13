"""Dev/test split of the golden dataset.

dev  (~50): you may look at these and tune prompts against them in M3.
test (~150): only scored for the final report.
"""

import random
from collections import defaultdict
from dataclasses import dataclass
from typing import Literal

# A type that allows only these two strings (Kotlin: a small enum class Split { DEV, TEST }).
Split = Literal["dev", "test"]


# INPUT: the minimum the split needs to know about one invoice.
@dataclass(frozen=True)
class SplitItem:
    id: str  # "inv-0042"
    group: str  # invoices of one scenario instance share a group ("g-017")
    scenario: str  # e.g. "duplicate_pair"


# E3 (yours): decide "dev" or "test" for every invoice.
def assign_splits(items: list[SplitItem], dev_size: int, seed: int) -> dict[str, Split]:
    """Map every item id to "dev" or "test".

    Rules (tests/test_split.py checks them):
      1. all invoices of one group land in the same split (no leakage),
      2. stratified: every scenario gets about the same dev share (dev_size / len(items)),
      3. about dev_size invoices in dev,
      4. deterministic for a given seed.
    """
    rng = random.Random(seed)  # our own seeded generator, never the global `random`

    # 1. group the invoices: group id -> its invoices (Kotlin: items.groupBy { it.group })
    groups: dict[str, list[SplitItem]] = defaultdict(list)
    for item in items:
        groups[item.group].append(item)

    # 2. scenario -> its group ids (all invoices of a group share one scenario)
    by_scenario: dict[str, list[str]] = defaultdict(list)
    for group_id, members in groups.items():
        by_scenario[members[0].scenario].append(group_id)

    share = dev_size / len(items)  # e.g. 50 / 200 = 0.25 -> a quarter of every scenario goes to dev
    result: dict[str, Split] = {}
    # 3. per scenario (sorted -> dict order cannot change the result)
    for scenario in sorted(by_scenario):
        group_ids = sorted(by_scenario[scenario])  # sort first, THEN shuffle -> same order for the same seed
        rng.shuffle(group_ids)
        target = round(sum(len(groups[g]) for g in group_ids) * share)  # dev invoices wanted for this scenario
        taken = 0
        # A group is one business case (e.g. both copies of a duplicate): splitting it would leak test data into dev.
        for group_id in group_ids:
            split: Split = "dev" if taken < target else "test"
            if split == "dev":
                taken += len(groups[group_id])
            for item in groups[group_id]:  # the whole group gets the same split
                result[item.id] = split
    return result
