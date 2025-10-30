import React from 'react';
import { Box, Typography } from '@mui/material';

const FileDetails: React.FC = () => {
  return (
    <Box sx={{ p: 3 }}>
      <Typography variant="h4">File Details</Typography>
      <Typography variant="body2" color="text.secondary">
        Detailed file information will be displayed here.
      </Typography>
    </Box>
  );
};

export default FileDetails;
