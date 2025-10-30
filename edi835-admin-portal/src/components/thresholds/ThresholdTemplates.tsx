import React from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Button,
  Grid,
  Chip,
  Paper,
  Divider,
} from '@mui/material';
import {
  FlashOn as QuickIcon,
  TrendingUp as HighVolumeIcon,
  Schedule as RegularIcon,
  Business as EnterpriseIcon,
} from '@mui/icons-material';
import { ThresholdType } from '../../types/models';

export interface ThresholdTemplate {
  id: string;
  name: string;
  description: string;
  icon: React.ReactNode;
  color: string;
  thresholdType: ThresholdType;
  maxClaims?: number;
  maxAmount?: number;
  timeDuration?: 'DAILY' | 'WEEKLY' | 'BIWEEKLY' | 'MONTHLY';
  category: 'quick' | 'standard' | 'enterprise';
}

const templates: ThresholdTemplate[] = [
  {
    id: 'quick-daily',
    name: 'Quick Daily Batch',
    description: 'Small daily batches for rapid processing and quick turnaround',
    icon: <QuickIcon />,
    color: '#4caf50',
    thresholdType: ThresholdType.HYBRID,
    maxClaims: 10,
    maxAmount: 5000,
    timeDuration: 'DAILY',
    category: 'quick',
  },
  {
    id: 'standard-daily',
    name: 'Standard Daily Processing',
    description: 'Balanced approach for medium-volume payers with daily submission requirements',
    icon: <RegularIcon />,
    color: '#2196f3',
    thresholdType: ThresholdType.HYBRID,
    maxClaims: 50,
    maxAmount: 25000,
    timeDuration: 'DAILY',
    category: 'standard',
  },
  {
    id: 'high-volume',
    name: 'High Volume Processing',
    description: 'Large batches optimized for high-volume payers with frequent submissions',
    icon: <HighVolumeIcon />,
    color: '#ff9800',
    thresholdType: ThresholdType.HYBRID,
    maxClaims: 100,
    maxAmount: 50000,
    timeDuration: 'DAILY',
    category: 'standard',
  },
  {
    id: 'weekly-batch',
    name: 'Weekly Reconciliation',
    description: 'Weekly batches for regular reporting and reconciliation cycles',
    icon: <RegularIcon />,
    color: '#9c27b0',
    thresholdType: ThresholdType.HYBRID,
    maxClaims: 200,
    maxAmount: 100000,
    timeDuration: 'WEEKLY',
    category: 'standard',
  },
  {
    id: 'enterprise-large',
    name: 'Enterprise Large Batch',
    description: 'Very large batches for enterprise-scale operations and major payers',
    icon: <EnterpriseIcon />,
    color: '#f44336',
    thresholdType: ThresholdType.HYBRID,
    maxClaims: 500,
    maxAmount: 250000,
    timeDuration: 'DAILY',
    category: 'enterprise',
  },
  {
    id: 'claim-count-only',
    name: 'Claim Count Only',
    description: 'Simple threshold based solely on number of claims',
    icon: <TrendingUp />,
    color: '#607d8b',
    thresholdType: ThresholdType.CLAIM_COUNT,
    maxClaims: 100,
    category: 'standard',
  },
  {
    id: 'amount-only',
    name: 'Amount Only',
    description: 'Simple threshold based solely on total dollar amount',
    icon: <AttachMoney />,
    color: '#795548',
    thresholdType: ThresholdType.AMOUNT,
    maxAmount: 50000,
    category: 'standard',
  },
  {
    id: 'time-only-daily',
    name: 'Time Only - Daily',
    description: 'Generate files once per day regardless of volume',
    icon: <Schedule />,
    color: '#00bcd4',
    thresholdType: ThresholdType.TIME,
    timeDuration: 'DAILY',
    category: 'quick',
  },
];

interface ThresholdTemplatesProps {
  onSelectTemplate: (template: ThresholdTemplate) => void;
  selectedCategory?: 'all' | 'quick' | 'standard' | 'enterprise';
}

// Import missing icons
import { TrendingUp, AttachMoney, Schedule } from '@mui/icons-material';

const ThresholdTemplates: React.FC<ThresholdTemplatesProps> = ({
  onSelectTemplate,
  selectedCategory = 'all',
}) => {
  const filteredTemplates =
    selectedCategory === 'all'
      ? templates
      : templates.filter((t) => t.category === selectedCategory);

  const getCategoryChipColor = (category: string) => {
    switch (category) {
      case 'quick':
        return 'success';
      case 'standard':
        return 'primary';
      case 'enterprise':
        return 'error';
      default:
        return 'default';
    }
  };

  return (
    <Box>
      <Typography variant="h6" gutterBottom sx={{ fontWeight: 600, mb: 1 }}>
        Quick Start Templates
      </Typography>
      <Typography variant="body2" color="textSecondary" sx={{ mb: 3 }}>
        Select a pre-configured template to get started quickly. You can customize the values after selection.
      </Typography>

      <Grid container spacing={2}>
        {filteredTemplates.map((template) => (
          <Grid item xs={12} sm={6} md={4} key={template.id}>
            <Card
              sx={{
                height: '100%',
                display: 'flex',
                flexDirection: 'column',
                cursor: 'pointer',
                transition: 'all 0.2s',
                '&:hover': {
                  boxShadow: 4,
                  transform: 'translateY(-4px)',
                },
              }}
              onClick={() => onSelectTemplate(template)}
            >
              <CardContent sx={{ flex: 1 }}>
                <Box display="flex" alignItems="center" gap={1} mb={1.5}>
                  <Box sx={{ color: template.color, display: 'flex' }}>
                    {template.icon}
                  </Box>
                  <Typography variant="h6" fontWeight={600} sx={{ flex: 1 }}>
                    {template.name}
                  </Typography>
                </Box>

                <Chip
                  label={template.category.toUpperCase()}
                  color={getCategoryChipColor(template.category)}
                  size="small"
                  sx={{ mb: 1.5 }}
                />

                <Typography variant="body2" color="textSecondary" sx={{ mb: 2 }}>
                  {template.description}
                </Typography>

                <Divider sx={{ my: 1.5 }} />

                <Box>
                  <Typography variant="caption" fontWeight={600} color="textSecondary">
                    Configuration:
                  </Typography>
                  <Paper variant="outlined" sx={{ p: 1, mt: 1, backgroundColor: '#fafafa' }}>
                    <Typography variant="caption" display="block">
                      <strong>Type:</strong> {template.thresholdType}
                    </Typography>
                    {template.maxClaims && (
                      <Typography variant="caption" display="block">
                        <strong>Max Claims:</strong> {template.maxClaims}
                      </Typography>
                    )}
                    {template.maxAmount && (
                      <Typography variant="caption" display="block">
                        <strong>Max Amount:</strong> ${template.maxAmount.toLocaleString()}
                      </Typography>
                    )}
                    {template.timeDuration && (
                      <Typography variant="caption" display="block">
                        <strong>Schedule:</strong> {template.timeDuration}
                      </Typography>
                    )}
                  </Paper>
                </Box>
              </CardContent>

              <Box sx={{ p: 2, pt: 0 }}>
                <Button
                  variant="outlined"
                  fullWidth
                  sx={{
                    borderColor: template.color,
                    color: template.color,
                    '&:hover': {
                      borderColor: template.color,
                      backgroundColor: `${template.color}20`,
                    },
                  }}
                >
                  Use This Template
                </Button>
              </Box>
            </Card>
          </Grid>
        ))}
      </Grid>
    </Box>
  );
};

export default ThresholdTemplates;
