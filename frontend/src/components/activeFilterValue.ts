export type ActiveFilterValue = 'active' | 'inactive' | 'all'

/** Maps the UI filter to the backend's optional `?active=` query parameter. */
export function toActiveParam(value: ActiveFilterValue): boolean | undefined {
  return value === 'all' ? undefined : value === 'active'
}
