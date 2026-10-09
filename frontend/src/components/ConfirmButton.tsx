import { Button, type ButtonProps, Group, Modal, Text } from '@mantine/core'
import { useDisclosure } from '@mantine/hooks'
import type { ReactNode } from 'react'

/** A button that asks for confirmation before running an irreversible business action. */
export function ConfirmButton(
  props: ButtonProps & { title: string; message: ReactNode; confirmLabel: string; onConfirm: () => void },
) {
  const { title, message, confirmLabel, onConfirm, ...buttonProps } = props
  const [opened, { open, close }] = useDisclosure()

  return (
    <>
      <Button {...buttonProps} onClick={open} />
      <Modal opened={opened} onClose={close} title={title} centered>
        <Text mb="lg">{message}</Text>
        <Group justify="flex-end">
          <Button variant="default" onClick={close}>
            Back
          </Button>
          <Button
            color={buttonProps.color}
            onClick={() => {
              close()
              onConfirm()
            }}
          >
            {confirmLabel}
          </Button>
        </Group>
      </Modal>
    </>
  )
}
