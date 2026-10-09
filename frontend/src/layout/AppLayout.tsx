import { Anchor, AppShell, Burger, Group, NavLink, Text, Title } from '@mantine/core'
import { useDisclosure } from '@mantine/hooks'
import { Link, Outlet, useLocation } from 'react-router'

interface NavItem {
  label: string
  to: string
}

/** Sidebar navigation, grouped like ERP modules. New features add their entry here. */
const navigation: { section: string; items: NavItem[] }[] = [
  {
    section: 'Master data',
    items: [
      { label: 'Suppliers', to: '/suppliers' },
      { label: 'Products', to: '/products' },
    ],
  },
  {
    section: 'Purchasing',
    items: [
      { label: 'Purchase orders', to: '/purchase-orders' },
      { label: 'Goods receipts', to: '/goods-receipts' },
    ],
  },
  {
    section: 'Accounts payable',
    items: [{ label: 'Supplier invoices', to: '/invoices' }],
  },
  {
    section: 'Inventory',
    items: [
      { label: 'Stock', to: '/inventory/stock' },
      { label: 'Stock movements', to: '/inventory/movements' },
      { label: 'Warehouses', to: '/inventory/warehouses' },
    ],
  },
]

export function AppLayout() {
  const [opened, { toggle, close }] = useDisclosure()
  const { pathname } = useLocation()

  return (
    <AppShell
      header={{ height: 56 }}
      navbar={{ width: 220, breakpoint: 'sm', collapsed: { mobile: !opened } }}
      padding="md"
    >
      <AppShell.Header>
        <Group h="100%" px="md">
          <Burger opened={opened} onClick={toggle} hiddenFrom="sm" size="sm" />
          <Anchor component={Link} to="/" underline="never" c="inherit">
            <Title order={4}>erpai</Title>
          </Anchor>
          <Text size="sm" c="dimmed">
            mini B2B ERP
          </Text>
        </Group>
      </AppShell.Header>

      <AppShell.Navbar p="xs">
        {navigation.map((group) => (
          <div key={group.section}>
            <Text size="xs" fw={700} c="dimmed" tt="uppercase" px="sm" pt="sm" pb={4}>
              {group.section}
            </Text>
            {group.items.map((item) => (
              <NavLink
                key={item.to}
                component={Link}
                to={item.to}
                label={item.label}
                active={pathname.startsWith(item.to)}
                onClick={close}
              />
            ))}
          </div>
        ))}
      </AppShell.Navbar>

      <AppShell.Main>
        <Outlet />
      </AppShell.Main>
    </AppShell>
  )
}
