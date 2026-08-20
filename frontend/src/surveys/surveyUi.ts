import { ApiError, type ApiFieldError } from '../api/apiClient.ts'

export function parseSurveyId(value: string | undefined): number | null {
  if (value === undefined || !/^[1-9][0-9]*$/.test(value)) {
    return null
  }
  const surveyId = Number(value)
  return Number.isSafeInteger(surveyId) ? surveyId : null
}

export function fieldMessage(
  errors: ApiFieldError[],
  path: string,
): string | undefined {
  return errors.find((error) => error.path === path)?.message
}

export function surveyErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    switch (error.code) {
      case 'SURVEY_SLUG_CONFLICT':
        return 'That reserved slug is already in use. Choose another slug.'
      case 'SURVEY_SLUG_IMMUTABLE':
        return 'The reserved slug cannot change after this Survey has opened.'
      case 'SURVEY_DELETE_REQUIRES_CLOSED':
        return 'Close this Survey before deleting it.'
      case 'SURVEY_STATE_CONFLICT':
        return 'The Survey lifecycle changed. Refresh and try the valid action.'
      case 'SURVEY_INVALID_STRUCTURE':
        return 'Add at least one valid Question before opening this Survey.'
      case 'SURVEY_STRUCTURE_LOCKED':
        return 'Existing Responses lock Question structure. Duplicate the Survey to make an editable copy.'
      case 'QUESTION_NOT_FOUND':
        return 'That Question is no longer available. Refresh the Builder.'
      case 'QUESTION_INVALID_CONFIGURATION':
      case 'VALIDATION_FAILED':
        return 'Review the highlighted fields and try again.'
      case 'CSRF_INVALID':
        return 'Your security token could not be refreshed. Try again.'
      case 'TEMPORARILY_UNAVAILABLE':
        return 'FormDock is temporarily unavailable. Try again.'
      case 'SURVEY_NOT_FOUND':
        return 'This Survey is unavailable or has been deleted.'
      case 'AUTH_REQUIRED':
      case 'AUTH_INVALID_CREDENTIALS':
      case 'FORBIDDEN':
      case 'UNEXPECTED_RESPONSE':
        return 'We could not complete that request safely. Try again.'
    }
  }
  return 'We could not complete that request safely. Try again.'
}

export function nullableText(value: string): string | null {
  return value.trim().length === 0 ? null : value
}
