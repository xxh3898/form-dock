import { describe, expect, it } from 'vitest'

import {
  buildQuestionInput,
  changeQuestionType,
  emptyQuestionForm,
  questionFormFrom,
} from './questionForm.ts'

describe('Question form payload normalization', () => {
  it('should_clearUnusedConfiguration_when_typeChanges', () => {
    const scale = {
      ...emptyQuestionForm('SCALE'),
      title: 'Scale question',
      scaleMin: '1',
      scaleMax: '7',
      scaleMinLabel: 'Low',
      scaleMaxLabel: 'High',
    }
    const number = changeQuestionType(scale, 'NUMBER')
    const result = buildQuestionInput({
      ...number,
      numberMin: '0.0001',
      numberMax: '999999999999999.9999',
    })

    expect(result.errors).toBeNull()
    expect(result.input).toEqual({
      type: 'NUMBER',
      title: 'Scale question',
      description: null,
      required: false,
      scaleMin: null,
      scaleMax: null,
      scaleMinLabel: null,
      scaleMaxLabel: null,
      numberMin: '0.0001',
      numberMax: '999999999999999.9999',
      options: [],
    })
  })

  it('should_preserveExistingOptionIdentity_andOmitNewIdentity', () => {
    const form = questionFormFrom({
      id: 10,
      type: 'SINGLE_CHOICE',
      title: 'Choose',
      description: null,
      required: true,
      position: 0,
      scaleMin: null,
      scaleMax: null,
      scaleMinLabel: null,
      scaleMaxLabel: null,
      numberMin: null,
      numberMax: null,
      options: [
        { id: 21, label: 'Existing', position: 0 },
        { id: 22, label: 'Second', position: 1 },
      ],
    })
    const result = buildQuestionInput({
      ...form,
      options: [...form.options, { key: 'new', label: 'New' }],
    })

    expect(result.errors).toBeNull()
    expect(result.input?.options).toEqual([
      { id: 21, label: 'Existing' },
      { id: 22, label: 'Second' },
      { label: 'New' },
    ])
  })

  it('should_preserveExistingOptionIdentity_when_choiceTypeChanges', () => {
    const singleChoice = questionFormFrom({
      id: 10,
      type: 'SINGLE_CHOICE',
      title: 'Choose',
      description: null,
      required: true,
      position: 0,
      scaleMin: null,
      scaleMax: null,
      scaleMinLabel: null,
      scaleMaxLabel: null,
      numberMin: null,
      numberMax: null,
      options: [
        { id: 21, label: 'Existing', position: 0 },
        { id: 22, label: 'Second', position: 1 },
      ],
    })

    const multipleChoice = changeQuestionType(
      singleChoice,
      'MULTIPLE_CHOICE',
    )
    const result = buildQuestionInput(multipleChoice)

    expect(result.errors).toBeNull()
    expect(result.input).toMatchObject({
      type: 'MULTIPLE_CHOICE',
      options: [
        { id: 21, label: 'Existing' },
        { id: 22, label: 'Second' },
      ],
    })
    expect(multipleChoice.options).toBe(singleChoice.options)
  })

  it('should_requireValidChoiceAndScaleConfiguration', () => {
    const choice = buildQuestionInput({
      ...emptyQuestionForm('MULTIPLE_CHOICE'),
      title: 'Choose',
    })
    expect(choice.errors).toMatchObject({
      'options[0].label': 'Option label is required.',
      'options[1].label': 'Option label is required.',
    })

    const scale = buildQuestionInput({
      ...emptyQuestionForm('SCALE'),
      title: 'Rate',
      scaleMin: '5',
      scaleMax: '5',
    })
    expect(scale.errors).toMatchObject({ scale: expect.any(String) })
  })
})
