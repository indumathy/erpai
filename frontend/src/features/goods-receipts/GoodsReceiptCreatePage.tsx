import { Alert, Anchor, Button, Group, Loader, NumberInput, Select, Stack, Table, Text, TextInput, Title } from '@mantine/core'
import { useForm } from '@mantine/form'
import { notifications } from '@mantine/notifications'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { handleFormError } from '../../api/errors'
import { formatQuantity, todayIsoDate } from '../../lib/format'
import { useWarehouses } from '../inventory/api'
import type { Warehouse } from '../inventory/types'
import { usePurchaseOrder, usePurchaseOrders } from '../purchase-orders/api'
import type { PurchaseOrder } from '../purchase-orders/types'
import { usePostGoodsReceipt } from './api'

/** Route /goods-receipts/new[?purchaseOrderId=..] */
export function GoodsReceiptCreatePage() {
  const [params, setParams] = useSearchParams()
  const purchaseOrderId = params.get('purchaseOrderId')

  return (
    <Stack>
      <Anchor component={Link} to="/goods-receipts" size="sm">
        ← Goods receipts
      </Anchor>
      <Title order={2}>Receive goods</Title>
      <PurchaseOrderPicker
        value={purchaseOrderId}
        onChange={(id) => setParams(id ? { purchaseOrderId: id } : {})}
      />
      {purchaseOrderId && <ReceiptFormLoader key={purchaseOrderId} purchaseOrderId={Number(purchaseOrderId)} />}
    </Stack>
  )
}

/** Only approved / partially received orders can receive goods. */
function PurchaseOrderPicker({ value, onChange }: { value: string | null; onChange: (id: string | null) => void }) {
  const orders = usePurchaseOrders(undefined)
  const receivable = (orders.data ?? []).filter((po) => po.status === 'APPROVED' || po.status === 'PARTIALLY_RECEIVED')

  return (
    <Select
      label="Purchase order"
      placeholder={orders.isPending ? 'Loading…' : 'Select an approved purchase order'}
      searchable
      maw={480}
      data={receivable.map((po) => ({
        value: String(po.id),
        label: `${po.poNumber} · ${po.supplier.name} (${po.status.replace('_', ' ')})`,
      }))}
      value={value}
      onChange={onChange}
    />
  )
}

function ReceiptFormLoader({ purchaseOrderId }: { purchaseOrderId: number }) {
  const order = usePurchaseOrder(purchaseOrderId)
  const warehouses = useWarehouses()

  if (order.isPending || warehouses.isPending) return <Loader />
  if (order.isError) return <Alert color="red">Could not load purchase order: {order.error.message}</Alert>
  if (warehouses.isError) return <Alert color="red">Could not load warehouses: {warehouses.error.message}</Alert>
  if (order.data.status !== 'APPROVED' && order.data.status !== 'PARTIALLY_RECEIVED') {
    return <Alert color="yellow">{order.data.poNumber} is {order.data.status}; goods cannot be received.</Alert>
  }
  return <ReceiptForm po={order.data} warehouses={warehouses.data.filter((w) => w.active)} />
}

interface FormValues {
  warehouseId: string | null
  receiptDate: string
  deliveryNoteNumber: string
  /** Quantity per PO item id; 0 = not part of this delivery. */
  quantities: Record<string, number | string>
}

function ReceiptForm({ po, warehouses }: { po: PurchaseOrder; warehouses: Warehouse[] }) {
  const navigate = useNavigate()
  const postReceipt = usePostGoodsReceipt()
  const openLines = po.items.filter((item) => item.openQuantity > 0)
  const defaultWarehouse = warehouses.find((w) => w.code === 'MAIN') ?? warehouses[0]

  const form = useForm<FormValues>({
    initialValues: {
      warehouseId: defaultWarehouse ? String(defaultWarehouse.id) : null,
      receiptDate: todayIsoDate(),
      deliveryNoteNumber: '',
      // Most deliveries are complete: pre-fill the open quantity, the user corrects shortfalls.
      quantities: Object.fromEntries(openLines.map((item) => [String(item.id), item.openQuantity])),
    },
    validate: {
      warehouseId: (v) => (v ? null : 'Select a warehouse'),
      receiptDate: (v) => (v ? null : 'Required'),
    },
  })

  const submit = form.onSubmit((values) => {
    const items = openLines
      .map((item) => ({ purchaseOrderItemId: item.id, quantity: Number(values.quantities[String(item.id)] || 0) }))
      .filter((line) => line.quantity > 0)

    if (items.length === 0) {
      notifications.show({ color: 'yellow', message: 'Enter a received quantity for at least one line' })
      return
    }

    postReceipt.mutate(
      {
        purchaseOrderId: po.id,
        warehouseId: Number(values.warehouseId),
        receiptDate: values.receiptDate,
        deliveryNoteNumber: values.deliveryNoteNumber.trim() || null,
        items,
      },
      {
        onSuccess: (gr) => {
          notifications.show({ color: 'green', message: `${gr.grNumber} posted` })
          void navigate(`/goods-receipts/${gr.id}`)
        },
        onError: (error) => handleFormError(error, form),
      },
    )
  })

  if (openLines.length === 0) {
    return <Alert color="teal">All lines of {po.poNumber} have been received in full.</Alert>
  }

  return (
    <form onSubmit={submit}>
      <Stack>
        <Group grow align="flex-start" maw={900}>
          <Select
            label="Warehouse"
            withAsterisk
            data={warehouses.map((w) => ({ value: String(w.id), label: `${w.code} · ${w.name}` }))}
            {...form.getInputProps('warehouseId')}
          />
          <TextInput label="Receipt date" type="date" withAsterisk {...form.getInputProps('receiptDate')} />
          <TextInput label="Supplier delivery note" placeholder="optional" {...form.getInputProps('deliveryNoteNumber')} />
        </Group>

        <Table>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>#</Table.Th>
              <Table.Th>SKU</Table.Th>
              <Table.Th>Description</Table.Th>
              <Table.Th ta="right">Ordered</Table.Th>
              <Table.Th ta="right">Already received</Table.Th>
              <Table.Th ta="right">Open</Table.Th>
              <Table.Th w={160}>Received now</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {openLines.map((item) => (
              <Table.Tr key={item.id}>
                <Table.Td>{item.lineNumber}</Table.Td>
                <Table.Td>{item.sku}</Table.Td>
                <Table.Td>{item.description}</Table.Td>
                <Table.Td ta="right">{formatQuantity(item.quantity)}</Table.Td>
                <Table.Td ta="right">{formatQuantity(item.receivedQuantity)}</Table.Td>
                <Table.Td ta="right">
                  {formatQuantity(item.openQuantity)} {item.unitOfMeasure}
                </Table.Td>
                <Table.Td>
                  <NumberInput
                    min={0}
                    max={item.openQuantity}
                    decimalScale={3}
                    hideControls
                    {...form.getInputProps(`quantities.${item.id}`)}
                  />
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>

        <Group justify="flex-end">
          <Text size="sm" c="dimmed">
            Lines with 0 are skipped. Posting updates the purchase order and stock immediately.
          </Text>
          <Button type="submit" loading={postReceipt.isPending}>
            Post goods receipt
          </Button>
        </Group>
      </Stack>
    </form>
  )
}
