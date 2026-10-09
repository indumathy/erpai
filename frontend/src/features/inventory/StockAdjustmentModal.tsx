import { Button, Group, Modal, NumberInput, Select, Stack, Text, TextInput } from '@mantine/core'
import { useForm } from '@mantine/form'
import { notifications } from '@mantine/notifications'
import { handleFormError } from '../../api/errors'
import { useProducts } from '../products/api'
import { useAdjustStock, useWarehouses } from './api'

interface FormValues {
  warehouseId: string | null
  productId: string | null
  quantity: number | string
  reason: string
}

/** Manual stock correction, e.g. after a physical count or for damaged goods. */
export function StockAdjustmentModal(props: { opened: boolean; onClose: () => void }) {
  const warehouses = useWarehouses()
  const products = useProducts(undefined) // inactive products may still have stock to correct
  const adjust = useAdjustStock()

  const form = useForm<FormValues>({
    initialValues: { warehouseId: null, productId: null, quantity: '', reason: '' },
    validate: {
      warehouseId: (v) => (v ? null : 'Select a warehouse'),
      productId: (v) => (v ? null : 'Select a product'),
      quantity: (v) => (v !== '' && Number(v) !== 0 ? null : 'Enter a positive or negative quantity'),
      reason: (v) => (v.trim() ? null : 'A reason is required for the audit trail'),
    },
  })

  const submit = form.onSubmit((values) =>
    adjust.mutate(
      {
        warehouseId: Number(values.warehouseId),
        productId: Number(values.productId),
        quantity: Number(values.quantity),
        reason: values.reason,
      },
      {
        onSuccess: () => {
          notifications.show({ color: 'green', message: 'Stock adjusted' })
          form.reset()
          props.onClose()
        },
        onError: (error) => handleFormError(error, form),
      },
    ),
  )

  return (
    <Modal opened={props.opened} onClose={props.onClose} title="Adjust stock">
      <form onSubmit={submit}>
        <Stack>
          <Select
            label="Warehouse"
            withAsterisk
            data={(warehouses.data ?? []).filter((w) => w.active).map((w) => ({ value: String(w.id), label: `${w.code} · ${w.name}` }))}
            {...form.getInputProps('warehouseId')}
          />
          <Select
            label="Product"
            searchable
            withAsterisk
            data={(products.data ?? []).map((p) => ({ value: String(p.id), label: `${p.sku} · ${p.name}` }))}
            {...form.getInputProps('productId')}
          />
          <NumberInput
            label="Quantity change"
            description="Positive adds stock, negative removes stock"
            decimalScale={3}
            withAsterisk
            {...form.getInputProps('quantity')}
          />
          <TextInput label="Reason" placeholder="Stock count 2026-10, damaged, ..." withAsterisk {...form.getInputProps('reason')} />
          <Text size="xs" c="dimmed">
            Stock can never go below zero; the server rejects such adjustments.
          </Text>
          <Group justify="flex-end">
            <Button variant="default" onClick={props.onClose}>
              Cancel
            </Button>
            <Button type="submit" loading={adjust.isPending}>
              Post adjustment
            </Button>
          </Group>
        </Stack>
      </form>
    </Modal>
  )
}
