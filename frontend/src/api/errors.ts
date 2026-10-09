import type { UseFormReturnType } from '@mantine/form'
import { notifications } from '@mantine/notifications'
import { ApiError } from './http'

/**
 * Shows a failed mutation to the user. Field-level validation errors from the backend
 * are attached to the matching form inputs; everything else becomes a notification.
 */
export function handleFormError<T>(error: unknown, form: UseFormReturnType<T>) {
  if (error instanceof ApiError && error.fieldErrors.length > 0) {
    // Backend paths look like "items[0].quantity"; Mantine form paths like "items.0.quantity".
    const toFormPath = (field: string) => field.replace(/\[(\d+)]/g, '.$1')
    form.setErrors(Object.fromEntries(error.fieldErrors.map((e) => [toFormPath(e.field), e.message])))
    return
  }
  showError(error)
}

export function showError(error: unknown) {
  notifications.show({
    color: 'red',
    title: error instanceof ApiError ? `Error ${error.status}` : 'Error',
    message: error instanceof Error ? error.message : 'Unexpected error',
  })
}
