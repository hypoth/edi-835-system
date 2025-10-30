import React from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Radio,
  RadioGroup,
  FormControlLabel,
  Chip,
  Paper,
} from '@mui/material';
import {
  TrendingUp as ClaimIcon,
  AttachMoney as AmountIcon,
  Schedule as TimeIcon,
  Dashboard as HybridIcon,
} from '@mui/icons-material';
import { ThresholdType } from '../../types/models';

interface ThresholdTypeSelectorProps {
  value: ThresholdType;
  onChange: (type: ThresholdType) => void;
  disabled?: boolean;
}

const thresholdTypeInfo = [
  {
    type: ThresholdType.CLAIM_COUNT,
    icon: <ClaimIcon />,
    label: 'Claim Count',
    description: 'Triggers generation when the number of claims in a bucket reaches a specified threshold',
    example: 'Generate file after 100 claims',
    color: '#1976d2' as const,
    useCases: [
      'High-volume payers with predictable claim rates',
      'Quality assurance batches',
      'Regulatory compliance requirements',
    ],
  },
  {
    type: ThresholdType.AMOUNT,
    icon: <AmountIcon />,
    label: 'Amount',
    description: 'Triggers generation when the total claim amount reaches a dollar threshold',
    example: 'Generate file when total reaches $50,000',
    color: '#9c27b0' as const,
    useCases: [
      'High-value transactions requiring immediate processing',
      'Cash flow management',
      'Financial reconciliation deadlines',
    ],
  },
  {
    type: ThresholdType.TIME,
    icon: <TimeIcon />,
    label: 'Time-Based',
    description: 'Triggers generation at scheduled intervals (daily, weekly, biweekly, monthly)',
    example: 'Generate file every day at 5 PM',
    color: '#2196f3' as const,
    useCases: [
      'Regular reporting schedules',
      'Payers requiring daily/weekly batches',
      'Consistent processing windows',
    ],
  },
  {
    type: ThresholdType.HYBRID,
    icon: <HybridIcon />,
    label: 'Hybrid (OR Logic)',
    description: 'Combines multiple conditions. Triggers when ANY condition is met (whichever comes first)',
    example: 'Generate at 100 claims OR $50,000 OR end of day',
    color: '#ff9800' as const,
    useCases: [
      'Flexible processing with multiple triggers',
      'Optimizing for both volume and timing',
      'Variable claim patterns requiring multiple safety valves',
    ],
  },
];

const ThresholdTypeSelector: React.FC<ThresholdTypeSelectorProps> = ({
  value,
  onChange,
  disabled = false,
}) => {
  return (
    <Box>
      <Typography variant="h6" gutterBottom sx={{ fontWeight: 600, mb: 2 }}>
        Select Threshold Type
      </Typography>
      <Typography variant="body2" color="textSecondary" sx={{ mb: 3 }}>
        Choose how the system should determine when to generate EDI files for accumulated claims.
      </Typography>

      <RadioGroup value={value} onChange={(e) => onChange(e.target.value as ThresholdType)}>
        {thresholdTypeInfo.map((info) => (
          <Card
            key={info.type}
            sx={{
              mb: 2,
              border: value === info.type ? `2px solid ${info.color}` : '1px solid #e0e0e0',
              cursor: disabled ? 'not-allowed' : 'pointer',
              transition: 'all 0.2s',
              '&:hover': disabled ? {} : {
                boxShadow: 2,
                borderColor: info.color,
              },
              opacity: disabled ? 0.6 : 1,
            }}
            onClick={() => !disabled && onChange(info.type)}
          >
            <CardContent>
              <Box display="flex" alignItems="flex-start">
                <FormControlLabel
                  value={info.type}
                  control={<Radio disabled={disabled} />}
                  label=""
                  sx={{ mr: 1 }}
                />
                <Box sx={{ flex: 1 }}>
                  <Box display="flex" alignItems="center" gap={1} mb={1}>
                    <Box sx={{ color: info.color, display: 'flex' }}>{info.icon}</Box>
                    <Typography variant="h6" fontWeight={600}>
                      {info.label}
                    </Typography>
                    {info.type === ThresholdType.HYBRID && (
                      <Chip label="Recommended" color="warning" size="small" />
                    )}
                  </Box>

                  <Typography variant="body2" color="textSecondary" sx={{ mb: 1 }}>
                    {info.description}
                  </Typography>

                  <Paper
                    variant="outlined"
                    sx={{
                      p: 1,
                      mb: 1.5,
                      backgroundColor: '#f5f5f5',
                      borderLeft: `3px solid ${info.color}`,
                    }}
                  >
                    <Typography
                      variant="caption"
                      sx={{ fontFamily: 'monospace', fontWeight: 500 }}
                    >
                      Example: {info.example}
                    </Typography>
                  </Paper>

                  <Box>
                    <Typography variant="caption" fontWeight={600} color="textSecondary">
                      Best for:
                    </Typography>
                    <Box component="ul" sx={{ mt: 0.5, mb: 0, pl: 2 }}>
                      {info.useCases.map((useCase, idx) => (
                        <Typography
                          key={idx}
                          component="li"
                          variant="caption"
                          color="textSecondary"
                        >
                          {useCase}
                        </Typography>
                      ))}
                    </Box>
                  </Box>
                </Box>
              </Box>
            </CardContent>
          </Card>
        ))}
      </RadioGroup>
    </Box>
  );
};

export default ThresholdTypeSelector;
