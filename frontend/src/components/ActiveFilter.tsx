import { Badge, SegmentedControl } from '@mantine/core'
import type { ActiveFilterValue } from './activeFilterValue'

export function ActiveFilter(props: { value: ActiveFilterValue; onChange: (value: ActiveFilterValue) => void }) {
  return (
    <SegmentedControl
      size="xs"
      value={props.value}
      onChange={(v) => props.onChange(v as ActiveFilterValue)}
      data={[
        { label: 'Active', value: 'active' },
        { label: 'Inactive', value: 'inactive' },
        { label: 'All', value: 'all' },
      ]}
    />
  )
}

export function ActiveBadge({ active }: { active: boolean }) {
  return (
    <Badge color={active ? 'green' : 'gray'} variant="light">
      {active ? 'Active' : 'Inactive'}
    </Badge>
  )
}
