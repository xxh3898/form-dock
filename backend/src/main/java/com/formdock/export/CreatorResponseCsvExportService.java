package com.formdock.export;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.formdock.question.Question;
import com.formdock.question.QuestionOption;
import com.formdock.question.QuestionRepository;
import com.formdock.question.QuestionType;
import com.formdock.response.CreatorResponseReadException;
import com.formdock.survey.SurveyRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreatorResponseCsvExportService {

    private final SurveyRepository surveyRepository;
    private final QuestionRepository questionRepository;
    private final CreatorResponseCsvRowRepository rowRepository;

    public CreatorResponseCsvExportService(
            SurveyRepository surveyRepository,
            QuestionRepository questionRepository,
            CreatorResponseCsvRowRepository rowRepository) {
        this.surveyRepository = surveyRepository;
        this.questionRepository = questionRepository;
        this.rowRepository = rowRepository;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public void export(
            Long ownerId,
            Long surveyId,
            CsvOutputStreamSupplier outputStreamSupplier) {
        surveyRepository.findByIdAndOwnerIdAndDeletedAtIsNull(surveyId, ownerId)
                .orElseThrow(CreatorResponseReadException::surveyNotFound);
        ExportLayout layout = ExportLayout.from(questionRepository
                .findAllWithOptionsBySurveyIdOrderByPosition(surveyId));

        try {
            OutputStream outputStream = outputStreamSupplier.open();
            Rfc4180CsvWriter writer = new Rfc4180CsvWriter(outputStream);
            writer.writeBom();
            writer.writeRecord(layout.headers(), layout.headerFormulaProtection());

            ResponseStreamProcessor processor = new ResponseStreamProcessor(layout, writer);
            rowRepository.streamBySurveyId(surveyId, processor::accept);
            processor.finish();
            writer.flush();
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to write CSV export", exception);
        }
    }

    private static String canonicalDecimal(BigDecimal value) {
        if (value == null) {
            throw new IllegalStateException("Persisted numeric Answer has no numeric value");
        }
        BigDecimal canonical = value.stripTrailingZeros();
        return canonical.signum() == 0 ? "0" : canonical.toPlainString();
    }

    private static final class ResponseStreamProcessor {

        private final ExportLayout layout;
        private final Rfc4180CsvWriter writer;
        private ResponseAccumulator current;
        private Instant previousSubmittedAt;
        private long previousResponseId;

        private ResponseStreamProcessor(ExportLayout layout, Rfc4180CsvWriter writer) {
            this.layout = layout;
            this.writer = writer;
        }

        private void accept(CreatorResponseCsvRowRepository.Row row) {
            if (current == null || current.responseId() != row.responseId()) {
                writeCurrent();
                verifyOrdering(row);
                current = layout.newResponse(row.responseId(), row.submittedAt());
                previousSubmittedAt = row.submittedAt();
                previousResponseId = row.responseId();
            }
            current.accept(row, layout);
        }

        private void finish() {
            writeCurrent();
        }

        private void writeCurrent() {
            if (current == null) {
                return;
            }
            try {
                writer.writeRecord(current.values(), layout.rowFormulaProtection());
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }

        private void verifyOrdering(CreatorResponseCsvRowRepository.Row row) {
            if (previousSubmittedAt == null) {
                return;
            }
            int timestampOrder = previousSubmittedAt.compareTo(row.submittedAt());
            if (timestampOrder > 0
                    || (timestampOrder == 0 && previousResponseId >= row.responseId())) {
                throw new IllegalStateException("CSV Response cursor order is not canonical");
            }
        }
    }

    private static final class ResponseAccumulator {

        private final long responseId;
        private final String[] values;

        private ResponseAccumulator(
                long responseId,
                Instant submittedAt,
                int columnCount,
                List<Integer> multipleChoiceColumns) {
            this.responseId = responseId;
            values = new String[columnCount];
            values[0] = Long.toString(responseId);
            values[1] = submittedAt.toString();
            multipleChoiceColumns.forEach(column -> values[column] = "false");
        }

        private void accept(
                CreatorResponseCsvRowRepository.Row row,
                ExportLayout layout) {
            if (row.questionId() == null) {
                return;
            }
            QuestionLayout question = layout.question(row.questionId());
            switch (question.type()) {
                case SHORT_TEXT, LONG_TEXT -> values[question.valueColumn()] = row.textValue();
                case SCALE, NUMBER ->
                    values[question.valueColumn()] = canonicalDecimal(row.numericValue());
                case SINGLE_CHOICE -> values[question.valueColumn()] = question
                        .singleChoiceValue(row.optionId());
                case MULTIPLE_CHOICE -> {
                    if (row.optionId() != null) {
                        values[question.multipleChoiceColumn(row.optionId())] = "true";
                    }
                }
            }
        }

        private long responseId() {
            return responseId;
        }

        private String[] values() {
            return values;
        }
    }

    private record ExportLayout(
            String[] headers,
            boolean[] headerFormulaProtection,
            boolean[] rowFormulaProtection,
            Map<Long, QuestionLayout> questions,
            List<Integer> multipleChoiceColumns) {

        private static ExportLayout from(List<Question> orderedQuestions) {
            List<String> headers = new ArrayList<>();
            List<Boolean> headerProtection = new ArrayList<>();
            List<Boolean> rowProtection = new ArrayList<>();
            Map<Long, QuestionLayout> questions = new HashMap<>();
            List<Integer> multipleChoiceColumns = new ArrayList<>();

            addColumn(headers, headerProtection, rowProtection, "response_id", false, false);
            addColumn(headers, headerProtection, rowProtection, "submitted_at", false, false);

            int expectedQuestionPosition = 0;
            for (Question question : orderedQuestions) {
                if (question.getPosition() != expectedQuestionPosition
                        || !question.hasCanonicalConfiguration()) {
                    throw new IllegalStateException(
                            "Persisted Question structure is not canonical");
                }
                expectedQuestionPosition++;
                if (question.getType() == QuestionType.MULTIPLE_CHOICE) {
                    Map<Long, Integer> optionColumns = new HashMap<>();
                    List<QuestionOption> options = question.getOptions().stream()
                            .sorted(Comparator.comparingInt(QuestionOption::getPosition))
                            .toList();
                    for (QuestionOption option : options) {
                        int column = headers.size();
                        addColumn(
                                headers,
                                headerProtection,
                                rowProtection,
                                "q_" + question.getId()
                                        + "_option_" + option.getId()
                                        + ": " + question.getTitle()
                                        + " / " + option.getLabel(),
                                true,
                                false);
                        optionColumns.put(option.getId(), column);
                        multipleChoiceColumns.add(column);
                    }
                    questions.put(
                            question.getId(),
                            new QuestionLayout(
                                    question.getType(),
                                    -1,
                                    Map.copyOf(optionColumns),
                                    Map.of()));
                    continue;
                }

                int valueColumn = headers.size();
                boolean dynamicRowValue = question.getType() == QuestionType.SHORT_TEXT
                        || question.getType() == QuestionType.LONG_TEXT
                        || question.getType() == QuestionType.SINGLE_CHOICE;
                addColumn(
                        headers,
                        headerProtection,
                        rowProtection,
                        "q_" + question.getId() + ": " + question.getTitle(),
                        true,
                        dynamicRowValue);
                Map<Long, String> optionLabels = question.getType() == QuestionType.SINGLE_CHOICE
                        ? question.getOptions().stream().collect(java.util.stream.Collectors.toMap(
                                QuestionOption::getId,
                                QuestionOption::getLabel))
                        : Map.of();
                questions.put(
                        question.getId(),
                        new QuestionLayout(
                                question.getType(),
                                valueColumn,
                                Map.of(),
                                Map.copyOf(optionLabels)));
            }

            return new ExportLayout(
                    headers.toArray(String[]::new),
                    toBooleanArray(headerProtection),
                    toBooleanArray(rowProtection),
                    Map.copyOf(questions),
                    List.copyOf(multipleChoiceColumns));
        }

        private static void addColumn(
                List<String> headers,
                List<Boolean> headerProtection,
                List<Boolean> rowProtection,
                String header,
                boolean protectHeader,
                boolean protectRow) {
            headers.add(header);
            headerProtection.add(protectHeader);
            rowProtection.add(protectRow);
        }

        private static boolean[] toBooleanArray(List<Boolean> values) {
            boolean[] result = new boolean[values.size()];
            for (int index = 0; index < values.size(); index++) {
                result[index] = values.get(index);
            }
            return result;
        }

        private QuestionLayout question(Long questionId) {
            QuestionLayout question = questions.get(questionId);
            if (question == null) {
                throw new IllegalStateException("Persisted Answer references unknown Question");
            }
            return question;
        }

        private ResponseAccumulator newResponse(long responseId, Instant submittedAt) {
            return new ResponseAccumulator(
                    responseId,
                    submittedAt,
                    headers.length,
                    multipleChoiceColumns);
        }
    }

    private record QuestionLayout(
            QuestionType type,
            int valueColumn,
            Map<Long, Integer> multipleChoiceColumns,
            Map<Long, String> singleChoiceLabels) {

        private int multipleChoiceColumn(Long optionId) {
            Integer column = multipleChoiceColumns.get(optionId);
            if (column == null) {
                throw new IllegalStateException(
                        "Persisted Answer references unknown MULTIPLE_CHOICE Option");
            }
            return column;
        }

        private String singleChoiceValue(Long optionId) {
            String label = singleChoiceLabels.get(optionId);
            if (label == null) {
                throw new IllegalStateException(
                        "Persisted Answer references unknown SINGLE_CHOICE Option");
            }
            return optionId + ": " + label;
        }
    }
}
