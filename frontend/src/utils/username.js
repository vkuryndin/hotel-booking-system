/** Shared UI validation of username; synchronized with Booking Service request DTOs. */
export const USERNAME_HTML_PATTERN = '[A-Za-z][A-Za-z0-9._\\-]{2,29}'
export const USERNAME_RULES = 'Логин: 3–30 символов; первый — латинская буква. Далее разрешены латинские буквы, цифры, точка, дефис и подчёркивание. Без пробелов и кириллицы.'

export function usernameValidationMessage(value) {
  const text = String(value ?? '')
  if (!text) return 'Введите логин.'
  if (text.length < 3 || text.length > 30) return 'Логин должен содержать от 3 до 30 символов.'
  if (!/^[A-Za-z]/.test(text)) return 'Логин должен начинаться с латинской буквы.'
  if (!/^[A-Za-z][A-Za-z0-9._-]{2,29}$/.test(text)) {
    return 'Логин может содержать только латинские буквы, цифры, точку, дефис и подчёркивание. Пробелы и кириллица не допускаются.'
  }
  return ''
}
