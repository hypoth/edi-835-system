import React from 'react';
import { Box, Typography } from '@mui/material';
import PayerList from '../../components/payers/PayerList';

const PayersConfig: React.FC = () => {
  return (
    <Box>
      {/* Page Header */}
      <Box mb={3}>
        <Typography variant="h4" fontWeight={600} gutterBottom>
          Payers Configuration
        </Typography>
        <Typography variant="body1" color="textSecondary">
          Manage insurance payers, EDI identifiers (ISA/GS), and SFTP delivery settings
        </Typography>
      </Box>

      {/* Payer List Component */}
      <PayerList />
    </Box>
  );
};

export default PayersConfig;
