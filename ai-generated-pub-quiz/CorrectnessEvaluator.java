package com.fortytwotalents.openai.spring.ai.evaluation;

import java.lang.reflect.Type;
import java.util.Collections;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.StructuredOutputConverter;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.evaluation.Evaluator;
import org.springframework.core.ParameterizedTypeReference;

/**
 * Evaluates the correctness of a generated answer.
 *
 * The evaluator relies on a reference answer to judge the correctness of the generated
 * answer.
 *
 * The evaluation response includes a score between 1 and 5, where 1 is the worst and 5 is
 * the best. The evaluator also provides reasoning for the score.
 *
 * Passing is determined by the score being greater than or equal to a threshold.
 *
 * @author Craig Walls
 * @author Patrick Baumgartner
 * @since 1.0.0 M2
 */
public class CorrectnessEvaluator implements Evaluator {

	private static final String DEFAULT_REFERENCE_ANSWER = "(NO REFERENCE ANSWER SUPPLIED)";

	private static final String DEFAULT_SYSTEM_PROMPT_TEXT = """
				You are an expert evaluation system for a question answering chatbot.
				You are given the following information:
				- a user query, and
				- a generated answer
				You may also be given a reference answer to use for reference in your evaluation.
				Your job is to judge the relevance and correctness of the generated answer.
				Output a single score that represents a holistic evaluation.
				You must return your response in a line with only the score.
				Do not return answers in any other format.
				On a separate line provide your reasoning for the score as well.
				Follow these guidelines for scoring:
				- Your score has to be between 1 and 5, where 1 is the worst and 5 is the best.
				- If the generated answer is not relevant to the user query,
				you should give a score of 1.
				- If the generated answer is relevant but contains mistakes,
				you should give a score between 2 and 3.
				- If the generated answer is relevant and fully correct,
				you should give a score between 4 and 5.
				Example Response:
				4.0
				The generated answer has the exact same metrics as the reference answer,
				but it is not as concise.
			""";

	private static final String DEFAULT_USER_PROMPT_TEMPLATE = """
				## User Query
				{query}
				## Reference Answer
				{reference_answer}
				## Generated Answer
				{generated_answer}
				## Format
				{format}
			""";

	private static final String DEFAULT_FORMAT_TEMPLATE = """
				Your response should be in JSON format.
				The data structure should only mention score and reasoning.
				Provide a RFC8259 compliant JSON response following this format without deviation.
			""";

	private final ChatClient.Builder chatClientBuilder;

	private float scoreThreshold = 4.0f;

	public CorrectnessEvaluator(ChatClient.Builder chatClientBuilder) {
		this.chatClientBuilder = chatClientBuilder;
	}

	public CorrectnessEvaluator(ChatClient.Builder chatClientBuilder, float scoreThreshold) {
		this.chatClientBuilder = chatClientBuilder;
		this.scoreThreshold = scoreThreshold;
	}

	@Override
	public EvaluationResponse evaluate(EvaluationRequest evaluationRequest) {
		final String referenceAnswer = (evaluationRequest instanceof CorrectnessEvaluationRequest)
				? ((CorrectnessEvaluationRequest) evaluationRequest).getReferenceAnswer() : DEFAULT_REFERENCE_ANSWER;

		var query = evaluationRequest.getUserText();
		var generatedAnswer = evaluationRequest.getResponseContent();

		CorrectnessEvaluation evaluationResult = this.chatClientBuilder.build()
			.prompt()
			.system(systemSpec -> systemSpec.text(DEFAULT_SYSTEM_PROMPT_TEXT))
			.user(userSpec -> userSpec.text(DEFAULT_USER_PROMPT_TEMPLATE)
				.param("query", query)
				.param("reference_answer", referenceAnswer)
				.param("generated_answer", generatedAnswer)
				.param("format", DEFAULT_FORMAT_TEMPLATE))
			.call()
			.entity(CorrectnessEvaluation.class);

		boolean passing = evaluationResult.score() >= this.scoreThreshold;

		return new EvaluationResponse(passing, evaluationResult.score(), evaluationResult.reasoning(),
				Collections.emptyMap());
	}

	private record CorrectnessEvaluation(float score, String reasoning) {
	}

}