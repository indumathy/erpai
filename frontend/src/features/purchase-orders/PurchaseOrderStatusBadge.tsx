import { Badge } from '@mantine/core'
import type { PurchaseOrderStatus } from './types'

const colors: Record<PurchaseOrderStatus, string> = {
  DRAFT: 'gray',
  APPROVED: 'blue',
  PARTIALLY_RECEIVED: 'yellow',
  RECEIVED: 'teal',
  CLOSED: 'dark',
  CANCELLED: 'red',
}

export function PurchaseOrderStatusBadge({ status }: { status: PurchaseOrderStatus }) {
  return (
    <Badge color={colors[status]} variant="light">
      {status.replace('_', ' ')}
    </Badge>
  )
}
