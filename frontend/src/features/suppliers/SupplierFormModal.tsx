import { Button, Group, Modal, NumberInput, Stack, TextInput } from '@mantine/core'
import { useForm } from '@mantine/form'
import { notifications } from '@mantine/notifications'
import { handleFormError } from '../../api/errors'
import { useCreateSupplier, useUpdateSupplier } from './api'
import type { Supplier } from './types'

interface FormValues {
  supplierNumber: string
  name: string
  vatId: string
  currency: string
  paymentTermsDays: number | string
}

/**
 * Create (supplier undefined) or edit a supplier.
 * Client-side checks are only for fast feedback; the backend remains the authority on all rules.
 */
export function SupplierFormModal(props: { supplier?: Supplier; opened: boolean; onClose: () => void }) {
  const { supplier } = props
  const isEdit = supplier !== undefined
  const create = useCreateSupplier()
  const update = useUpdateSupplier()

  const form = useForm<FormValues>({
    initialValues: {
      supplierNumber: supplier?.supplierNumber ?? '',
      name: supplier?.name ?? '',
      vatId: supplier?.vatId ?? '',
      currency: supplier?.currency ?? 'EUR',
      paymentTermsDays: supplier?.paymentTermsDays ?? 30,
    },
    validate: {
      supplierNumber: (v) => (v.trim() ? null : 'Required'),
      name: (v) => (v.trim() ? null : 'Required'),
      currency: (v) => (/^[A-Z]{3}$/.test(v) ? null : 'ISO 4217 code, e.g. EUR'),
    },
  })

  const submit = form.onSubmit((values) => {
    const body = {
      name: values.name,
      vatId: values.vatId.trim() || null,
      currency: values.currency,
      paymentTermsDays: Number(values.paymentTermsDays),
    }
    const onSuccess = () => {
      notifications.show({ color: 'green', message: isEdit ? 'Supplier updated' : 'Supplier created' })
      props.onClose()
    }
    const onError = (error: unknown) => handleFormError(error, form)

    if (isEdit) {
      update.mutate({ id: supplier.id, body }, { onSuccess, onError })
    } else {
      create.mutate({ ...body, supplierNumber: values.supplierNumber }, { onSuccess, onError })
    }
  })

  return (
    <Modal opened={props.opened} onClose={props.onClose} title={isEdit ? 'Edit supplier' : 'New supplier'}>
      <form onSubmit={submit}>
        <Stack>
          <TextInput
            label="Supplier number"
            placeholder="S-1000"
            disabled={isEdit}
            withAsterisk
            {...form.getInputProps('supplierNumber')}
          />
          <TextInput label="Name" withAsterisk {...form.getInputProps('name')} />
          <TextInput label="VAT ID" placeholder="DE123456789" {...form.getInputProps('vatId')} />
          <Group grow>
            <TextInput
              label="Currency"
              maxLength={3}
              withAsterisk
              {...form.getInputProps('currency')}
              onChange={(e) => form.setFieldValue('currency', e.currentTarget.value.toUpperCase())}
            />
            <NumberInput
              label="Payment terms (days)"
              min={0}
              max={365}
              allowDecimal={false}
              withAsterisk
              {...form.getInputProps('paymentTermsDays')}
            />
          </Group>
          <Group justify="flex-end">
            <Button variant="default" onClick={props.onClose}>
              Cancel
            </Button>
            <Button type="submit" loading={create.isPending || update.isPending}>
              Save
            </Button>
          </Group>
        </Stack>
      </form>
    </Modal>
  )
}
