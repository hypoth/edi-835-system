import React from 'react';
import {
  Box,
  TextField,
  MenuItem,
  FormControlLabel,
  Switch,
  Alert,
  InputAdornment,
} from '@mui/material';
import { GenerationThreshold, ThresholdType, BucketingRule } from '../../types/models';

interface ThresholdFormProps {
  formData: Partial<GenerationThreshold>;
  onChange: (data: Partial<GenerationThreshold>) => void;
  bucketingRules?: BucketingRule[];
  errors?: Record<string, string>;
  disabled?: boolean;
}

const ThresholdForm: React.FC<ThresholdFormProps> = ({
  formData,
  onChange,
  bucketingRules = [],
  errors = {},
  disabled = false,
}) => {
  const handleFieldChange = (field: keyof GenerationThreshold, value: any) => {
    onChange({ ...formData, [field]: value });
  };

  const showClaimCountField =
    formData.thresholdType === ThresholdType.CLAIM_COUNT ||
    formData.thresholdType === ThresholdType.HYBRID;

  const showAmountField =
    formData.thresholdType === ThresholdType.AMOUNT ||
    formData.thresholdType === ThresholdType.HYBRID;

  const showTimeDurationField =
    formData.thresholdType === ThresholdType.TIME ||
    formData.thresholdType === ThresholdType.HYBRID;

  return (
    <Box>
      <TextField
        fullWidth
        label="Threshold Name"
        value={formData.thresholdName || ''}
        onChange={(e) => handleFieldChange('thresholdName', e.target.value)}
        error={!!errors.thresholdName}
        helperText={errors.thresholdName || 'Enter a descriptive name for this threshold'}
        sx={{ mb: 2 }}
        required
        disabled={disabled}
        placeholder="e.g., BCBS Daily Processing"
      />

      <TextField
        fullWidth
        select
        label="Threshold Type"
        value={formData.thresholdType || ThresholdType.CLAIM_COUNT}
        onChange={(e) => handleFieldChange('thresholdType', e.target.value as ThresholdType)}
        error={!!errors.thresholdType}
        helperText={
          errors.thresholdType ||
          'Select how this threshold should trigger file generation'
        }
        sx={{ mb: 2 }}
        required
        disabled={disabled}
      >
        <MenuItem value={ThresholdType.CLAIM_COUNT}>
          Claim Count - Trigger at X claims
        </MenuItem>
        <MenuItem value={ThresholdType.AMOUNT}>
          Amount - Trigger at $X total
        </MenuItem>
        <MenuItem value={ThresholdType.TIME}>
          Time-Based - Daily/Weekly/Monthly
        </MenuItem>
        <MenuItem value={ThresholdType.HYBRID}>
          Hybrid - Multiple conditions (OR logic)
        </MenuItem>
      </TextField>

      {/* Conditional Fields Based on Threshold Type */}
      {showClaimCountField && (
        <TextField
          fullWidth
          label="Max Claims"
          type="number"
          value={formData.maxClaims || ''}
          onChange={(e) => handleFieldChange('maxClaims', parseInt(e.target.value) || undefined)}
          error={!!errors.maxClaims}
          helperText={
            errors.maxClaims ||
            'Generate file when claim count reaches this value'
          }
          sx={{ mb: 2 }}
          required={formData.thresholdType === ThresholdType.CLAIM_COUNT}
          disabled={disabled}
          InputProps={{
            inputProps: { min: 1, step: 1 },
            endAdornment: <InputAdornment position="end">claims</InputAdornment>,
          }}
        />
      )}

      {showAmountField && (
        <TextField
          fullWidth
          label="Max Amount"
          type="number"
          value={formData.maxAmount || ''}
          onChange={(e) =>
            handleFieldChange('maxAmount', parseFloat(e.target.value) || undefined)
          }
          error={!!errors.maxAmount}
          helperText={
            errors.maxAmount ||
            'Generate file when total amount reaches this value'
          }
          sx={{ mb: 2 }}
          required={formData.thresholdType === ThresholdType.AMOUNT}
          disabled={disabled}
          InputProps={{
            startAdornment: <InputAdornment position="start">$</InputAdornment>,
            inputProps: { min: 0, step: 100 },
          }}
        />
      )}

      {showTimeDurationField && (
        <TextField
          fullWidth
          select
          label="Time Duration"
          value={formData.timeDuration || ''}
          onChange={(e) => handleFieldChange('timeDuration', e.target.value as any)}
          error={!!errors.timeDuration}
          helperText={
            errors.timeDuration ||
            'Select the frequency for automatic file generation'
          }
          sx={{ mb: 2 }}
          required={formData.thresholdType === ThresholdType.TIME}
          disabled={disabled}
        >
          <MenuItem value="DAILY">Daily - Generate once per day</MenuItem>
          <MenuItem value="WEEKLY">Weekly - Generate once per week</MenuItem>
          <MenuItem value="BIWEEKLY">Bi-Weekly - Generate every 2 weeks</MenuItem>
          <MenuItem value="MONTHLY">Monthly - Generate once per month</MenuItem>
        </TextField>
      )}

      {/* Hybrid Type Info */}
      {formData.thresholdType === ThresholdType.HYBRID && (
        <Alert severity="info" sx={{ mb: 2 }}>
          <strong>Hybrid Mode:</strong> File generation will trigger when ANY condition is met
          (whichever comes first). For example, if you set 100 claims OR $50,000 OR daily, the
          file will generate at 100 claims even if the amount hasn't reached $50,000.
        </Alert>
      )}

      <TextField
        fullWidth
        select
        label="Linked Bucketing Rule"
        value={formData.linkedBucketingRule?.id || formData.linkedBucketingRule?.ruleId || ''}
        onChange={(e) => {
          const rule = bucketingRules.find((r) => (r.id || r.ruleId) === e.target.value);
          handleFieldChange('linkedBucketingRule', rule);
        }}
        error={!!errors.linkedBucketingRule}
        helperText={
          errors.linkedBucketingRule ||
          'Apply this threshold to a specific rule, or leave blank to apply to all rules'
        }
        sx={{ mb: 2 }}
        disabled={disabled}
      >
        <MenuItem value="">
          <em>All Rules</em>
        </MenuItem>
        {bucketingRules.map((rule) => (
          <MenuItem key={rule.id || rule.ruleId} value={rule.id || rule.ruleId}>
            {rule.ruleName} ({rule.ruleType})
          </MenuItem>
        ))}
      </TextField>

      <FormControlLabel
        control={
          <Switch
            checked={formData.isActive ?? true}
            onChange={(e) => handleFieldChange('isActive', e.target.checked)}
            disabled={disabled}
          />
        }
        label="Active"
      />

      {formData.isActive === false && (
        <Alert severity="warning" sx={{ mt: 2 }}>
          This threshold will not trigger file generation while inactive. Active buckets will
          continue to accumulate claims but won't transition to approval.
        </Alert>
      )}
    </Box>
  );
};

export default ThresholdForm;
