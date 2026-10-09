import { Anchor, List, Stack, Text, Title } from '@mantine/core'
import { Link } from 'react-router'

export function HomePage() {
  return (
    <Stack maw={640}>
      <Title order={2}>Welcome to erpai</Title>
      <Text>
        A small open-source B2B ERP for the Procure-to-Pay process: suppliers, products, purchase orders,
        goods receipts, supplier invoices and deterministic three-way invoice reconciliation.
      </Text>
      <Text fw={600}>Typical flow:</Text>
      <List type="ordered">
        <List.Item>
          Create{' '}
          <Anchor component={Link} to="/suppliers">
            suppliers
          </Anchor>{' '}
          and{' '}
          <Anchor component={Link} to="/products">
            products
          </Anchor>
        </List.Item>
        <List.Item>
          Draft and approve a{' '}
          <Anchor component={Link} to="/purchase-orders/new">
            purchase order
          </Anchor>
        </List.Item>
        <List.Item>
          <Anchor component={Link} to="/goods-receipts/new">
            Receive the goods
          </Anchor>{' '}
          and watch the{' '}
          <Anchor component={Link} to="/inventory/stock">
            stock
          </Anchor>{' '}
          grow
        </List.Item>
      </List>
    </Stack>
  )
}
