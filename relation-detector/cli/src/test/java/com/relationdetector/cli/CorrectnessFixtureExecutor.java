package com.relationdetector.cli;

final class CorrectnessFixtureExecutor {
    private final FixtureInputLoader inputLoader = new FixtureInputLoader();
    private final FixtureExecutionEngine executionEngine = new FixtureExecutionEngine(inputLoader);
    private final GoldenAssertion goldenAssertion = new GoldenAssertion();

    void runFixture(CorrectnessFixture fixture) throws Exception {
        LoadedFixtureInput input = inputLoader.load(fixture);
        FixtureActualResult actual = executionEngine.execute(fixture, input);
        goldenAssertion.assertFixture(fixture, input, actual);
    }
}
