import React, { useState } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Button,
  TextField,
  Grid,
  Divider,
  Alert,
  Chip,
  Paper,
  MenuItem,
} from '@mui/material';
import {
  PlayArrow as RunIcon,
  CheckCircle as PassIcon,
  Cancel as FailIcon,
} from '@mui/icons-material';
import { GenerationThreshold, ThresholdType } from '../../types/models';

interface TestScenario {
  claimCount: number;
  totalAmount: number;
  timeElapsed: string;
}

interface TestResult {
  wouldTrigger: boolean;
  reason: string;
  details: string[];
}

interface ThresholdTestingProps {
  threshold: Partial<GenerationThreshold>;
  onRunTest?: (scenario: TestScenario) => Promise<TestResult>;
}

const ThresholdTesting: React.FC<ThresholdTestingProps> = ({
  threshold,
  onRunTest,
}) => {
  const [scenario, setScenario] = useState<TestScenario>({
    claimCount: 50,
    totalAmount: 25000,
    timeElapsed: 'SAME_DAY',
  });

  const [testResult, setTestResult] = useState<TestResult | null>(null);
  const [isRunning, setIsRunning] = useState(false);

  const runLocalTest = (): TestResult => {
    const details: string[] = [];
    let wouldTrigger = false;

    if (!threshold.thresholdType) {
      return {
        wouldTrigger: false,
        reason: 'No threshold type configured',
        details: ['Configure threshold type before testing'],
      };
    }

    // Test claim count condition
    if (
      (threshold.thresholdType === ThresholdType.CLAIM_COUNT ||
        threshold.thresholdType === ThresholdType.HYBRID) &&
      threshold.maxClaims
    ) {
      const claimConditionMet = scenario.claimCount >= threshold.maxClaims;
      details.push(
        `Claim Count: ${scenario.claimCount} / ${threshold.maxClaims} - ${
          claimConditionMet ? 'PASS ✓' : 'FAIL ✗'
        }`
      );
      if (claimConditionMet) wouldTrigger = true;
    }

    // Test amount condition
    if (
      (threshold.thresholdType === ThresholdType.AMOUNT ||
        threshold.thresholdType === ThresholdType.HYBRID) &&
      threshold.maxAmount
    ) {
      const amountConditionMet = scenario.totalAmount >= threshold.maxAmount;
      details.push(
        `Total Amount: $${scenario.totalAmount.toLocaleString()} / $${threshold.maxAmount.toLocaleString()} - ${
          amountConditionMet ? 'PASS ✓' : 'FAIL ✗'
        }`
      );
      if (amountConditionMet) wouldTrigger = true;
    }

    // Test time condition
    if (
      (threshold.thresholdType === ThresholdType.TIME ||
        threshold.thresholdType === ThresholdType.HYBRID) &&
      threshold.timeDuration
    ) {
      const timeConditionMet = scenario.timeElapsed === threshold.timeDuration;
      details.push(
        `Time Duration: ${scenario.timeElapsed} (Expected: ${threshold.timeDuration}) - ${
          timeConditionMet ? 'PASS ✓' : 'FAIL ✗'
        }`
      );
      if (timeConditionMet) wouldTrigger = true;
    }

    const reason = wouldTrigger
      ? threshold.thresholdType === ThresholdType.HYBRID
        ? 'At least one condition met (OR logic)'
        : 'Threshold condition met'
      : 'No conditions met';

    return { wouldTrigger, reason, details };
  };

  const handleRunTest = async () => {
    setIsRunning(true);
    try {
      let result: TestResult;
      if (onRunTest) {
        result = await onRunTest(scenario);
      } else {
        result = runLocalTest();
      }
      setTestResult(result);
    } catch (error) {
      setTestResult({
        wouldTrigger: false,
        reason: 'Test failed',
        details: ['Error running test: ' + (error as Error).message],
      });
    } finally {
      setIsRunning(false);
    }
  };

  const predefinedScenarios = [
    {
      name: 'Low Volume',
      claimCount: 5,
      totalAmount: 2500,
      timeElapsed: 'SAME_DAY',
    },
    {
      name: 'Medium Volume',
      claimCount: 50,
      totalAmount: 25000,
      timeElapsed: 'SAME_DAY',
    },
    {
      name: 'High Volume',
      claimCount: 100,
      totalAmount: 50000,
      timeElapsed: 'SAME_DAY',
    },
    {
      name: 'End of Day',
      claimCount: 10,
      totalAmount: 5000,
      timeElapsed: 'DAILY',
    },
    {
      name: 'End of Week',
      claimCount: 200,
      totalAmount: 100000,
      timeElapsed: 'WEEKLY',
    },
  ];

  return (
    <Box>
      <Typography variant="h6" gutterBottom sx={{ fontWeight: 600, mb: 1 }}>
        Threshold Testing
      </Typography>
      <Typography variant="body2" color="textSecondary" sx={{ mb: 3 }}>
        Test your threshold configuration with simulated bucket scenarios to verify it triggers correctly.
      </Typography>

      <Grid container spacing={3}>
        {/* Test Scenario Configuration */}
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Test Scenario
              </Typography>

              <TextField
                fullWidth
                label="Claim Count"
                type="number"
                value={scenario.claimCount}
                onChange={(e) =>
                  setScenario({ ...scenario, claimCount: parseInt(e.target.value) || 0 })
                }
                sx={{ mb: 2 }}
                InputProps={{
                  inputProps: { min: 0, step: 1 },
                }}
              />

              <TextField
                fullWidth
                label="Total Amount ($)"
                type="number"
                value={scenario.totalAmount}
                onChange={(e) =>
                  setScenario({ ...scenario, totalAmount: parseFloat(e.target.value) || 0 })
                }
                sx={{ mb: 2 }}
                InputProps={{
                  inputProps: { min: 0, step: 100 },
                }}
              />

              <TextField
                fullWidth
                select
                label="Time Elapsed"
                value={scenario.timeElapsed}
                onChange={(e) => setScenario({ ...scenario, timeElapsed: e.target.value })}
                sx={{ mb: 2 }}
              >
                <MenuItem value="SAME_DAY">Same Day</MenuItem>
                <MenuItem value="DAILY">End of Day (Daily)</MenuItem>
                <MenuItem value="WEEKLY">End of Week (Weekly)</MenuItem>
                <MenuItem value="BIWEEKLY">End of Bi-Week</MenuItem>
                <MenuItem value="MONTHLY">End of Month</MenuItem>
              </TextField>

              <Button
                fullWidth
                variant="contained"
                startIcon={<RunIcon />}
                onClick={handleRunTest}
                disabled={isRunning}
                size="large"
              >
                {isRunning ? 'Running Test...' : 'Run Test'}
              </Button>

              <Divider sx={{ my: 2 }} />

              <Typography variant="subtitle2" gutterBottom>
                Quick Scenarios
              </Typography>
              <Box display="flex" flexWrap="wrap" gap={1}>
                {predefinedScenarios.map((preset) => (
                  <Chip
                    key={preset.name}
                    label={preset.name}
                    onClick={() => setScenario(preset)}
                    variant="outlined"
                    size="small"
                  />
                ))}
              </Box>
            </CardContent>
          </Card>
        </Grid>

        {/* Test Results */}
        <Grid item xs={12} md={6}>
          <Card sx={{ height: '100%' }}>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Test Results
              </Typography>

              {!testResult ? (
                <Alert severity="info">
                  Configure a test scenario and click "Run Test" to see results.
                </Alert>
              ) : (
                <Box>
                  <Paper
                    sx={{
                      p: 2,
                      mb: 2,
                      backgroundColor: testResult.wouldTrigger ? '#fff3e0' : '#f5f5f5',
                      border: testResult.wouldTrigger
                        ? '2px solid #ff9800'
                        : '1px solid #e0e0e0',
                    }}
                  >
                    <Box display="flex" alignItems="center" gap={1} mb={1}>
                      {testResult.wouldTrigger ? (
                        <PassIcon color="warning" fontSize="large" />
                      ) : (
                        <FailIcon color="disabled" fontSize="large" />
                      )}
                      <Typography variant="h5" fontWeight={600}>
                        {testResult.wouldTrigger
                          ? 'Would Trigger'
                          : 'Would Not Trigger'}
                      </Typography>
                    </Box>
                    <Typography variant="body2" color="textSecondary">
                      {testResult.reason}
                    </Typography>
                  </Paper>

                  <Typography variant="subtitle2" gutterBottom fontWeight={600}>
                    Condition Evaluation:
                  </Typography>
                  <Paper variant="outlined" sx={{ p: 2, backgroundColor: '#fafafa' }}>
                    {testResult.details.length > 0 ? (
                      testResult.details.map((detail, idx) => (
                        <Typography
                          key={idx}
                          variant="body2"
                          sx={{
                            mb: 1,
                            fontFamily: 'monospace',
                            fontSize: '0.85rem',
                          }}
                        >
                          {detail}
                        </Typography>
                      ))
                    ) : (
                      <Typography variant="body2" color="textSecondary">
                        No conditions configured
                      </Typography>
                    )}
                  </Paper>

                  {threshold.thresholdType === ThresholdType.HYBRID && (
                    <Alert severity="info" sx={{ mt: 2 }}>
                      <Typography variant="caption">
                        <strong>Hybrid Mode:</strong> File triggers when ANY condition passes
                        (OR logic)
                      </Typography>
                    </Alert>
                  )}
                </Box>
              )}
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Threshold Configuration Summary */}
      <Card sx={{ mt: 3 }}>
        <CardContent>
          <Typography variant="subtitle2" gutterBottom fontWeight={600}>
            Current Threshold Configuration:
          </Typography>
          <Grid container spacing={2}>
            <Grid item xs={12} sm={3}>
              <Typography variant="caption" color="textSecondary">
                Type
              </Typography>
              <Typography variant="body2" fontWeight={600}>
                {threshold.thresholdType || 'Not set'}
              </Typography>
            </Grid>
            <Grid item xs={12} sm={3}>
              <Typography variant="caption" color="textSecondary">
                Max Claims
              </Typography>
              <Typography variant="body2" fontWeight={600}>
                {threshold.maxClaims || 'N/A'}
              </Typography>
            </Grid>
            <Grid item xs={12} sm={3}>
              <Typography variant="caption" color="textSecondary">
                Max Amount
              </Typography>
              <Typography variant="body2" fontWeight={600}>
                {threshold.maxAmount
                  ? `$${threshold.maxAmount.toLocaleString()}`
                  : 'N/A'}
              </Typography>
            </Grid>
            <Grid item xs={12} sm={3}>
              <Typography variant="caption" color="textSecondary">
                Time Duration
              </Typography>
              <Typography variant="body2" fontWeight={600}>
                {threshold.timeDuration || 'N/A'}
              </Typography>
            </Grid>
          </Grid>
        </CardContent>
      </Card>
    </Box>
  );
};

export default ThresholdTesting;
