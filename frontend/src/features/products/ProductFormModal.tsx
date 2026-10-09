import { Button, Group, Modal, Select, Stack, Textarea, TextInput } from '@mantine/core'
import { useForm } from '@mantine/form'
import { notifications } from '@mantine/notifications'
import { handleFormError } from '../../api/errors'
import { useCreateProduct, useUpdateProduct } from './api'
import { type Product, UNITS_OF_MEASURE, type UnitOfMeasure } from './types'

interface FormValues {
  sku: string
  name: string
  description: string
  unitOfMeasure: UnitOfMeasure
}

export function ProductFormModal(props: { product?: Product; opened: boolean; onClose: () => void }) {
  const { product } = props
  const isEdit = product !== undefined
  const create = useCreateProduct()
  const update = useUpdateProduct()

  const form = useForm<FormValues>({
    initialValues: {
      sku: product?.sku ?? '',
      name: product?.name ?? '',
      description: product?.description ?? '',
      unitOfMeasure: product?.unitOfMeasure ?? 'PIECE',
    },
    validate: {
      sku: (v) => (v.trim() ? null : 'Required'),
      name: (v) => (v.trim() ? null : 'Required'),
    },
  })

  const submit = form.onSubmit((values) => {
    const body = {
      name: values.name,
      description: values.description.trim() || null,
      unitOfMeasure: values.unitOfMeasure,
    }
    const onSuccess = () => {
      notifications.show({ color: 'green', message: isEdit ? 'Product updated' : 'Product created' })
      props.onClose()
    }
    const onError = (error: unknown) => handleFormError(error, form)

    if (isEdit) {
      update.mutate({ id: product.id, body }, { onSuccess, onError })
    } else {
      create.mutate({ ...body, sku: values.sku }, { onSuccess, onError })
    }
  })

  return (
    <Modal opened={props.opened} onClose={props.onClose} title={isEdit ? 'Edit product' : 'New product'}>
      <form onSubmit={submit}>
        <Stack>
          <TextInput label="SKU" placeholder="BOLT-M8-40" disabled={isEdit} withAsterisk {...form.getInputProps('sku')} />
          <TextInput label="Name" withAsterisk {...form.getInputProps('name')} />
          <Textarea label="Description" autosize minRows={2} {...form.getInputProps('description')} />
          <Select
            label="Unit of measure"
            data={UNITS_OF_MEASURE}
            allowDeselect={false}
            withAsterisk
            {...form.getInputProps('unitOfMeasure')}
          />
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
