import { Alert, Button, Group, Loader, Modal, Stack, Table, TextInput, Title } from '@mantine/core'
import { useForm } from '@mantine/form'
import { useDisclosure } from '@mantine/hooks'
import { notifications } from '@mantine/notifications'
import { handleFormError, showError } from '../../api/errors'
import { ActiveBadge } from '../../components/ActiveFilter'
import { useCreateWarehouse, useSetWarehouseActive, useWarehouses } from './api'

export function WarehousesPage() {
  const warehouses = useWarehouses()
  const setActive = useSetWarehouseActive()
  const [creating, { open, close }] = useDisclosure()

  return (
    <>
      <Group justify="space-between" mb="md">
        <Title order={2}>Warehouses</Title>
        <Button onClick={open}>New warehouse</Button>
      </Group>

      {warehouses.isPending && <Loader />}
      {warehouses.isError && <Alert color="red">Could not load warehouses: {warehouses.error.message}</Alert>}
      {warehouses.isSuccess && (
        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Code</Table.Th>
              <Table.Th>Name</Table.Th>
              <Table.Th>Status</Table.Th>
              <Table.Th />
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {warehouses.data.map((w) => (
              <Table.Tr key={w.id}>
                <Table.Td>{w.code}</Table.Td>
                <Table.Td>{w.name}</Table.Td>
                <Table.Td>
                  <ActiveBadge active={w.active} />
                </Table.Td>
                <Table.Td ta="right">
                  <Button
                    size="xs"
                    variant="subtle"
                    color={w.active ? 'red' : 'green'}
                    onClick={() => setActive.mutate({ id: w.id, active: !w.active }, { onError: showError })}
                  >
                    {w.active ? 'Deactivate' : 'Activate'}
                  </Button>
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      )}

      {creating && <WarehouseFormModal onClose={close} />}
    </>
  )
}

function WarehouseFormModal({ onClose }: { onClose: () => void }) {
  const create = useCreateWarehouse()
  const form = useForm({
    initialValues: { code: '', name: '' },
    validate: {
      code: (v) => (v.trim() ? null : 'Required'),
      name: (v) => (v.trim() ? null : 'Required'),
    },
  })

  const submit = form.onSubmit((values) =>
    create.mutate(values, {
      onSuccess: () => {
        notifications.show({ color: 'green', message: 'Warehouse created' })
        onClose()
      },
      onError: (error) => handleFormError(error, form),
    }),
  )

  return (
    <Modal opened onClose={onClose} title="New warehouse">
      <form onSubmit={submit}>
        <Stack>
          <TextInput label="Code" placeholder="WH-2" maxLength={16} withAsterisk {...form.getInputProps('code')} />
          <TextInput label="Name" placeholder="Hamburg distribution centre" withAsterisk {...form.getInputProps('name')} />
          <Group justify="flex-end">
            <Button variant="default" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" loading={create.isPending}>
              Save
            </Button>
          </Group>
        </Stack>
      </form>
    </Modal>
  )
}
