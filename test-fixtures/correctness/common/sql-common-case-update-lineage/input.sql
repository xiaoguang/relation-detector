UPDATE customer_scores cs
SET score_bucket = CASE
  WHEN cs.score > 90 THEN cs.score
  ELSE cs.base_score
END;
