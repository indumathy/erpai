import {
  Alert,
  Anchor,
  Button,
  CloseButton,
  Group,
  Loader,
  NumberInput,
  Select,
  Stack,
  Table,
  Text,
  TextInput,
  Title,
} from '@mantine/core'
import { useForm } from '@mantine/form'
import { randomId } from '@mantine/hooks'
import { notifications } from '@mantine/notifications'
import { Link, useNavigate, useParams } from 'react-router'
import { handleFormError } from '../../api/errors'
import { todayIsoDate } from '../../lib/format'
import { useProducts } from '../products/api'
import { useSuppliers } from '../suppliers/api'
import { useCreatePurchaseOrder, usePurchaseOrder, useUpdatePurchaseOrder } from './api'
import type { PurchaseOrder, PurchaseOrderItemInput } from './types'

interface LineValues {
  key: string // stable React key while lines are added/removed
  productId: string | null
  description: string
  quantity: number | string
  unitPrice: number | string
  taxRate: number | string
}

interface FormValues {
  supplierId: string | null
  orderDate: string
  currency: string
  items: LineValues[]
}

const DEFAULT_TAX_RATE = 19

const emptyLine = (): LineValues => ({
  key: randomId(),
  productId: null,
  description: '',
  quantity: 1,
  unitPrice: 0,
  taxRate: DEFAULT_TAX_RATE,
})

/** Route /purchase-orders/new */
export function PurchaseOrderCreatePage() {
  return (
    <PurchaseOrderForm
      initialValues={{ supplierId: null, orderDate: todayIsoDate(), currency: 'EUR', items: [emptyLine()] }}
    />
  )
}

/** Route /purchase-orders/:id/edit (only DRAFT orders can be edited). */
export function PurchaseOrderEditPage() {
  const id = Number(useParams().id)
  const order = usePurchaseOrder(id)

  if (order.isPending) return <Loader />
  if (order.isError) return <Alert color="red">Could not load purchase order: {order.error.message}</Alert>
  if (order.data.status !== 'DRAFT') {
    return <Alert color="yellow">{order.data.poNumber} is {order.data.status} and can no longer be edited.</Alert>
  }
  return <PurchaseOrderForm existing={order.data} initialValues={toFormValues(order.data)} />
}

function toFormValues(po: PurchaseOrder): FormValues {
  return {
    supplierId: String(po.supplier.id),
    orderDate: po.orderDate,
    currency: po.currency,
    items: po.items.map((item) => ({
      key: randomId(),
      productId: String(item.productId),
      description: item.description,
      quantity: item.quantity,
      unitPrice: item.unitPrice,
      taxRate: item.taxRate,
    })),
  }
}

function PurchaseOrderForm({ existing, initialValues }: { existing?: PurchaseOrder; initialValues: FormValues }) {
  const navigate = useNavigate()
  const suppliers = useSuppliers(true)
  const products = useProducts(true)
  const create = useCreatePurchaseOrder()
  const update = useUpdatePurchaseOrder()

  const form = useForm<FormValues>({
    initialValues,
    validate: {
      supplierId: (v) => (v ? null : 'Select a supplier'),
      orderDate: (v) => (v ? null : 'Required'),
      currency: (v) => (/^[A-Z]{3}$/.test(v) ? null : 'ISO 4217 code, e.g. EUR'),
      items: {
        productId: (v) => (v ? null : 'Select a product'),
        quantity: (v) => (Number(v) > 0 ? null : 'Must be greater than 0'),
        unitPrice: (v) => (v !== '' && Number(v) >= 0 ? null : 'Must not be negative'),
        taxRate: (v) => (v !== '' && Number(v) >= 0 && Number(v) <= 100 ? null : '0 to 100'),
      },
    },
  })

  const supplierOptions = (suppliers.data ?? []).map((s) => ({
    value: String(s.id),
    label: `${s.supplierNumber} · ${s.name}`,
  }))
  const productOptions = (products.data ?? []).map((p) => ({ value: String(p.id), label: `${p.sku} · ${p.name}` }))

  const onSupplierChange = (supplierId: string | null) => {
    form.setFieldValue('supplierId', supplierId)
    // A new PO defaults to the supplier's currency.
    const supplier = suppliers.data?.find((s) => String(s.id) === supplierId)
    if (supplier) form.setFieldValue('currency', supplier.currency)
  }

  const submit = form.onSubmit((values) => {
    const items: PurchaseOrderItemInput[] = values.items.map((line) => ({
      productId: Number(line.productId),
      description: line.description.trim() || null,
      quantity: Number(line.quantity),
      unitPrice: Number(line.unitPrice),
      taxRate: Number(line.taxRate),
    }))
    const body = { orderDate: values.orderDate, currency: values.currency, items }
    const callbacks = {
      onSuccess: (po: PurchaseOrder) => {
        notifications.show({ color: 'green', message: `${po.poNumber} saved` })
        void navigate(`/purchase-orders/${po.id}`)
      },
      onError: (error: unknown) => handleFormError(error, form),
    }

    if (existing) {
      update.mutate({ id: existing.id, body }, callbacks)
    } else {
      create.mutate({ ...body, supplierId: Number(values.supplierId) }, callbacks)
    }
  })

  return (
    <form onSubmit={submit}>
      <Stack>
        <Anchor component={Link} to={existing ? `/purchase-orders/${existing.id}` : '/purchase-orders'} size="sm">
          ← Back
        </Anchor>
        <Title order={2}>{existing ? `Edit ${existing.poNumber}` : 'New purchase order'}</Title>

        <Group grow align="flex-start">
          <Select
            label="Supplier"
            placeholder="Search supplier"
            searchable
            withAsterisk
            disabled={existing !== undefined}
            data={supplierOptions}
            {...form.getInputProps('supplierId')}
            onChange={onSupplierChange}
          />
          <TextInput label="Order date" type="date" withAsterisk {...form.getInputProps('orderDate')} />
          <TextInput
            label="Currency"
            maxLength={3}
            withAsterisk
            {...form.getInputProps('currency')}
            onChange={(e) => form.setFieldValue('currency', e.currentTarget.value.toUpperCase())}
          />
        </Group>

        <Table>
          <Table.Thead>
            <Table.Tr>
              <Table.Th w="30%">Product</Table.Th>
              <Table.Th>Description (optional)</Table.Th>
              <Table.Th w={110}>Quantity</Table.Th>
              <Table.Th w={140}>Unit price</Table.Th>
              <Table.Th w={100}>Tax %</Table.Th>
              <Table.Th w={40} />
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {form.values.items.map((line, index) => (
              <Table.Tr key={line.key}>
                <Table.Td>
                  <Select
                    placeholder="Search product"
                    searchable
                    data={productOptions}
                    {...form.getInputProps(`items.${index}.productId`)}
                  />
                </Table.Td>
                <Table.Td>
                  <TextInput placeholder="defaults to product name" {...form.getInputProps(`items.${index}.description`)} />
                </Table.Td>
                <Table.Td>
                  <NumberInput min={0} decimalScale={3} hideControls {...form.getInputProps(`items.${index}.quantity`)} />
                </Table.Td>
                <Table.Td>
                  <NumberInput min={0} decimalScale={4} hideControls {...form.getInputProps(`items.${index}.unitPrice`)} />
                </Table.Td>
                <Table.Td>
                  <NumberInput
                    min={0}
                    max={100}
                    decimalScale={2}
                    hideControls
                    {...form.getInputProps(`items.${index}.taxRate`)}
                  />
                </Table.Td>
                <Table.Td>
                  <CloseButton
                    aria-label="Remove line"
                    disabled={form.values.items.length === 1}
                    onClick={() => form.removeListItem('items', index)}
                  />
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>

        <Group justify="space-between">
          <Button variant="light" onClick={() => form.insertListItem('items', emptyLine())}>
            Add line
          </Button>
          <Group>
            <Text size="sm" c="dimmed">
              Totals are calculated by the server when you save.
            </Text>
            <Button type="submit" loading={create.isPending || update.isPending}>
              {existing ? 'Save changes' : 'Create draft'}
            </Button>
          </Group>
        </Group>
      </Stack>
    </form>
  )
}
