import { Badge } from '@mantine/core'

export function MatchBadge({ exceptionCount }: { exceptionCount: number }) {
  if (exceptionCount === 0) return <Badge color="green">Matched</Badge>
  return <Badge color="red">{exceptionCount === 1 ? '1 exception' : `${exceptionCount} exceptions`}</Badge>
}
