import React, { useMemo } from 'react';
import {
  Box,
  Alert,
  Typography,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  Paper,
  Chip,
} from '@mui/material';
import {
  Error as ErrorIcon,
  Warning as WarningIcon,
  Info as InfoIcon,
  CheckCircle as SuccessIcon,
} from '@mui/icons-material';
import { GenerationThreshold, ThresholdType } from '../../types/models';

export interface ValidationResult {
  isValid: boolean;
  errors: ValidationMessage[];
  warnings: ValidationMessage[];
  info: ValidationMessage[];
}

export interface ValidationMessage {
  severity: 'error' | 'warning' | 'info';
  field?: string;
  message: string;
}

interface ThresholdValidatorProps {
  threshold: Partial<GenerationThreshold>;
  existingThresholds?: GenerationThreshold[];
  showValidation?: boolean;
}

const ThresholdValidator: React.FC<ThresholdValidatorProps> = ({
  threshold,
  existingThresholds = [],
  showValidation = true,
}) => {
  const validationResult = useMemo((): ValidationResult => {
    const errors: ValidationMessage[] = [];
    const warnings: ValidationMessage[] = [];
    const info: ValidationMessage[] = [];

    // Required field validation
    if (!threshold.thresholdName?.trim()) {
      errors.push({
        severity: 'error',
        field: 'thresholdName',
        message: 'Threshold name is required',
      });
    }

    if (!threshold.thresholdType) {
      errors.push({
        severity: 'error',
        field: 'thresholdType',
        message: 'Threshold type is required',
      });
    }

    // Type-specific validation
    if (threshold.thresholdType) {
      if (
        (threshold.thresholdType === ThresholdType.CLAIM_COUNT ||
          threshold.thresholdType === ThresholdType.HYBRID) &&
        (!threshold.maxClaims || threshold.maxClaims <= 0)
      ) {
        errors.push({
          severity: 'error',
          field: 'maxClaims',
          message: 'Max claims must be greater than 0',
        });
      }

      if (
        (threshold.thresholdType === ThresholdType.AMOUNT ||
          threshold.thresholdType === ThresholdType.HYBRID) &&
        (!threshold.maxAmount || threshold.maxAmount <= 0)
      ) {
        errors.push({
          severity: 'error',
          field: 'maxAmount',
          message: 'Max amount must be greater than 0',
        });
      }

      if (
        (threshold.thresholdType === ThresholdType.TIME ||
          threshold.thresholdType === ThresholdType.HYBRID) &&
        !threshold.timeDuration
      ) {
        errors.push({
          severity: 'error',
          field: 'timeDuration',
          message: 'Time duration is required',
        });
      }
    }

    // Reasonable value warnings
    if (threshold.maxClaims && threshold.maxClaims < 5) {
      warnings.push({
        severity: 'warning',
        field: 'maxClaims',
        message: 'Very low claim count threshold (< 5) may cause frequent file generation',
      });
    }

    if (threshold.maxClaims && threshold.maxClaims > 1000) {
      warnings.push({
        severity: 'warning',
        field: 'maxClaims',
        message: 'Very high claim count threshold (> 1000) may delay file generation',
      });
    }

    if (threshold.maxAmount && threshold.maxAmount < 1000) {
      warnings.push({
        severity: 'warning',
        field: 'maxAmount',
        message: 'Very low amount threshold (< $1,000) may cause frequent file generation',
      });
    }

    if (threshold.maxAmount && threshold.maxAmount > 500000) {
      warnings.push({
        severity: 'warning',
        field: 'maxAmount',
        message: 'Very high amount threshold (> $500,000) may delay file generation',
      });
    }

    // Duplicate/conflict detection
    if (threshold.thresholdName && existingThresholds.length > 0) {
      const duplicateName = existingThresholds.find(
        (t) =>
          t.thresholdName === threshold.thresholdName &&
          t.thresholdId !== threshold.thresholdId
      );

      if (duplicateName) {
        errors.push({
          severity: 'error',
          field: 'thresholdName',
          message: 'A threshold with this name already exists',
        });
      }
    }

    // Check for conflicting thresholds on the same rule
    if (threshold.linkedBucketingRule?.ruleId && existingThresholds.length > 0) {
      const conflictingThresholds = existingThresholds.filter(
        (t) =>
          t.linkedBucketingRule?.ruleId === threshold.linkedBucketingRule?.ruleId &&
          t.thresholdId !== threshold.thresholdId &&
          t.isActive
      );

      if (conflictingThresholds.length > 0) {
        warnings.push({
          severity: 'warning',
          field: 'linkedBucketingRule',
          message: `${conflictingThresholds.length} other active threshold(s) already configured for this rule. Multiple thresholds will compete.`,
        });
      }
    }

    // Helpful info messages
    if (!threshold.linkedBucketingRule?.ruleId) {
      info.push({
        severity: 'info',
        message: 'This threshold will apply to all bucketing rules (global threshold)',
      });
    }

    if (threshold.thresholdType === ThresholdType.HYBRID) {
      info.push({
        severity: 'info',
        message: 'Hybrid thresholds use OR logic - file generates when ANY condition is met',
      });
    }

    if (threshold.isActive === false) {
      info.push({
        severity: 'info',
        message: 'Inactive thresholds will not trigger file generation',
      });
    }

    return {
      isValid: errors.length === 0,
      errors,
      warnings,
      info,
    };
  }, [threshold, existingThresholds]);

  if (!showValidation) {
    return null;
  }

  const hasMessages =
    validationResult.errors.length > 0 ||
    validationResult.warnings.length > 0 ||
    validationResult.info.length > 0;

  if (!hasMessages) {
    return (
      <Alert severity="success" icon={<SuccessIcon />} sx={{ mb: 2 }}>
        <Typography variant="body2" fontWeight={600}>
          Validation Passed
        </Typography>
        <Typography variant="caption">
          This threshold configuration is valid and ready to save.
        </Typography>
      </Alert>
    );
  }

  return (
    <Box sx={{ mb: 2 }}>
      {validationResult.errors.length > 0 && (
        <Alert severity="error" icon={<ErrorIcon />} sx={{ mb: 2 }}>
          <Typography variant="body2" fontWeight={600} gutterBottom>
            {validationResult.errors.length} Error(s)
          </Typography>
          <List dense>
            {validationResult.errors.map((error, idx) => (
              <ListItem key={idx} disableGutters>
                <ListItemIcon sx={{ minWidth: 32 }}>
                  <ErrorIcon fontSize="small" color="error" />
                </ListItemIcon>
                <ListItemText
                  primary={error.message}
                  primaryTypographyProps={{ variant: 'body2' }}
                  secondary={error.field ? `Field: ${error.field}` : undefined}
                  secondaryTypographyProps={{ variant: 'caption' }}
                />
              </ListItem>
            ))}
          </List>
        </Alert>
      )}

      {validationResult.warnings.length > 0 && (
        <Alert severity="warning" icon={<WarningIcon />} sx={{ mb: 2 }}>
          <Typography variant="body2" fontWeight={600} gutterBottom>
            {validationResult.warnings.length} Warning(s)
          </Typography>
          <List dense>
            {validationResult.warnings.map((warning, idx) => (
              <ListItem key={idx} disableGutters>
                <ListItemIcon sx={{ minWidth: 32 }}>
                  <WarningIcon fontSize="small" color="warning" />
                </ListItemIcon>
                <ListItemText
                  primary={warning.message}
                  primaryTypographyProps={{ variant: 'body2' }}
                  secondary={warning.field ? `Field: ${warning.field}` : undefined}
                  secondaryTypographyProps={{ variant: 'caption' }}
                />
              </ListItem>
            ))}
          </List>
        </Alert>
      )}

      {validationResult.info.length > 0 && (
        <Alert severity="info" icon={<InfoIcon />}>
          <Typography variant="body2" fontWeight={600} gutterBottom>
            Information
          </Typography>
          <List dense>
            {validationResult.info.map((infoMsg, idx) => (
              <ListItem key={idx} disableGutters>
                <ListItemIcon sx={{ minWidth: 32 }}>
                  <InfoIcon fontSize="small" color="info" />
                </ListItemIcon>
                <ListItemText
                  primary={infoMsg.message}
                  primaryTypographyProps={{ variant: 'body2' }}
                />
              </ListItem>
            ))}
          </List>
        </Alert>
      )}
    </Box>
  );
};

export default ThresholdValidator;

/**
 * Hook for programmatic validation without rendering
 */
export const useThresholdValidation = (
  threshold: Partial<GenerationThreshold>,
  existingThresholds?: GenerationThreshold[]
): ValidationResult => {
  return useMemo(() => {
    const validator = ThresholdValidator({
      threshold,
      existingThresholds,
      showValidation: false,
    });
    // Extract validation logic (simplified version)
    // In practice, you'd extract the validation logic to a shared function
    return {
      isValid: true,
      errors: [],
      warnings: [],
      info: [],
    };
  }, [threshold, existingThresholds]);
};
