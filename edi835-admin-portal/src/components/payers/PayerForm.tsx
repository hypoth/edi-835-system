import React, { useState } from 'react';
import {
  Box,
  Button,
  TextField,
  Grid,
  Typography,
  Paper,
  Switch,
  FormControlLabel,
  Divider,
  Alert,
  CircularProgress,
} from '@mui/material';
import { useFormik } from 'formik';
import * as yup from 'yup';
import { Payer } from '../../types/models';
import { configurationService } from '../../services/configurationService';
import { toast } from 'react-toastify';

interface PayerFormProps {
  payer?: Payer;
  onSuccess?: (payer: Payer) => void;
  onCancel?: () => void;
}

const validationSchema = yup.object({
  payerId: yup
    .string()
    .required('Payer ID is required')
    .max(50, 'Payer ID must not exceed 50 characters')
    .matches(/^[A-Z0-9_]+$/, 'Payer ID must contain only uppercase letters, numbers, and underscores'),
  payerName: yup
    .string()
    .required('Payer name is required')
    .min(3, 'Payer name must be at least 3 characters'),
  isaQualifier: yup
    .string()
    .matches(/^[A-Z0-9]{2}$/, 'ISA Qualifier must be exactly 2 characters (e.g., ZZ, 01, 30)')
    .default('ZZ'),
  isaSenderId: yup
    .string()
    .required('ISA Sender ID is required for EDI 835 generation')
    .max(15, 'ISA Sender ID must not exceed 15 characters')
    .matches(/^[A-Z0-9]+$/, 'ISA Sender ID must contain only uppercase letters and numbers'),
  gsApplicationSenderId: yup
    .string()
    .max(15, 'GS Application Sender ID must not exceed 15 characters')
    .matches(/^[A-Z0-9]*$/, 'GS Application Sender ID must contain only uppercase letters and numbers'),
  addressStreet: yup.string().max(255, 'Address street must not exceed 255 characters'),
  addressCity: yup.string().max(100, 'City must not exceed 100 characters'),
  addressState: yup
    .string()
    .matches(/^[A-Z]{0,2}$/, 'State must be 2 uppercase letters (e.g., CA, NY)')
    .max(2, 'State must be exactly 2 characters'),
  addressZip: yup
    .string()
    .matches(/^[0-9]{0,5}(-[0-9]{4})?$/, 'ZIP must be in format: 12345 or 12345-6789')
    .max(10, 'ZIP must not exceed 10 characters'),
  sftpHost: yup.string().max(255, 'SFTP Host must not exceed 255 characters'),
  sftpPort: yup
    .number()
    .min(1, 'Port must be between 1 and 65535')
    .max(65535, 'Port must be between 1 and 65535')
    .nullable(),
  sftpUsername: yup.string().max(100, 'SFTP Username must not exceed 100 characters'),
  sftpPassword: yup.string().max(200, 'SFTP Password must not exceed 200 characters'),
  sftpPath: yup.string().max(500, 'SFTP Path must not exceed 500 characters'),
});

const PayerForm: React.FC<PayerFormProps> = ({ payer, onSuccess, onCancel }) => {
  const [loading, setLoading] = useState(false);
  const isEditMode = !!payer;

  const formik = useFormik({
    initialValues: {
      payerId: payer?.payerId || '',
      payerName: payer?.payerName || '',
      isaQualifier: payer?.isaQualifier || 'ZZ',
      isaSenderId: payer?.isaSenderId || '',
      gsApplicationSenderId: payer?.gsApplicationSenderId || '',
      addressStreet: payer?.addressStreet || '',
      addressCity: payer?.addressCity || '',
      addressState: payer?.addressState || '',
      addressZip: payer?.addressZip || '',
      sftpHost: payer?.sftpHost || '',
      sftpPort: payer?.sftpPort || null,
      sftpUsername: payer?.sftpUsername || '',
      sftpPassword: payer?.sftpPassword || '',
      sftpPath: payer?.sftpPath || '',
      requiresSpecialHandling: payer?.requiresSpecialHandling || false,
      isActive: payer?.isActive ?? true,
    },
    validationSchema: validationSchema,
    onSubmit: async (values) => {
      setLoading(true);
      try {
        let savedPayer: Payer;

        if (isEditMode && payer?.id) {
          savedPayer = await configurationService.updatePayer(payer.id, values);
          toast.success('Payer updated successfully');
        } else {
          savedPayer = await configurationService.createPayer(values);
          toast.success('Payer created successfully');
        }

        if (onSuccess) {
          onSuccess(savedPayer);
        }

        if (!isEditMode) {
          formik.resetForm();
        }
      } catch (error: any) {
        console.error('Error saving payer:', error);
        const errorMessage = error.response?.data?.message || error.message || 'Failed to save payer';
        toast.error(errorMessage);
      } finally {
        setLoading(false);
      }
    },
  });

  const handleAutoFillIsaSenderId = () => {
    if (formik.values.payerId && !formik.values.isaSenderId) {
      const generated = `${formik.values.payerId}ISA`.substring(0, 15);
      formik.setFieldValue('isaSenderId', generated);
    }
  };

  const handleAutoFillGsApplicationSenderId = () => {
    if (formik.values.payerId && !formik.values.gsApplicationSenderId) {
      const generated = `${formik.values.payerId}GS`.substring(0, 15);
      formik.setFieldValue('gsApplicationSenderId', generated);
    }
  };

  return (
    <Paper elevation={2} sx={{ p: 3 }}>
      <Typography variant="h5" gutterBottom>
        {isEditMode ? 'Edit Payer' : 'Add New Payer'}
      </Typography>

      <Box component="form" onSubmit={formik.handleSubmit} noValidate>
        {/* Basic Information */}
        <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
          Basic Information
        </Typography>
        <Grid container spacing={3}>
          <Grid item xs={12} md={6}>
            <TextField
              fullWidth
              id="payerId"
              name="payerId"
              label="Payer ID *"
              value={formik.values.payerId}
              onChange={formik.handleChange}
              onBlur={(e) => {
                formik.handleBlur(e);
                handleAutoFillIsaSenderId();
                handleAutoFillGsApplicationSenderId();
              }}
              error={formik.touched.payerId && Boolean(formik.errors.payerId)}
              helperText={
                (formik.touched.payerId && formik.errors.payerId) ||
                'Unique identifier for the payer (e.g., BCBS001, UHC001)'
              }
              disabled={isEditMode}
            />
          </Grid>

          <Grid item xs={12} md={6}>
            <TextField
              fullWidth
              id="payerName"
              name="payerName"
              label="Payer Name *"
              value={formik.values.payerName}
              onChange={formik.handleChange}
              onBlur={formik.handleBlur}
              error={formik.touched.payerName && Boolean(formik.errors.payerName)}
              helperText={
                (formik.touched.payerName && formik.errors.payerName) ||
                'Full name of the payer organization'
              }
            />
          </Grid>
        </Grid>

        {/* EDI Configuration */}
        <Divider sx={{ my: 3 }} />
        <Typography variant="h6" sx={{ mb: 2 }}>
          EDI Configuration
        </Typography>
        <Alert severity="info" sx={{ mb: 2 }}>
          These fields are required for generating HIPAA-compliant EDI 835 files
        </Alert>

        <Grid container spacing={3}>
          <Grid item xs={12} md={4}>
            <TextField
              fullWidth
              id="isaQualifier"
              name="isaQualifier"
              label="ISA Qualifier"
              value={formik.values.isaQualifier}
              onChange={formik.handleChange}
              onBlur={formik.handleBlur}
              error={formik.touched.isaQualifier && Boolean(formik.errors.isaQualifier)}
              helperText={
                (formik.touched.isaQualifier && formik.errors.isaQualifier) ||
                'ISA segment qualifier (default: ZZ)'
              }
              inputProps={{ maxLength: 2, style: { textTransform: 'uppercase' } }}
            />
          </Grid>

          <Grid item xs={12} md={4}>
            <TextField
              fullWidth
              id="isaSenderId"
              name="isaSenderId"
              label="ISA Sender ID *"
              value={formik.values.isaSenderId}
              onChange={formik.handleChange}
              onBlur={formik.handleBlur}
              error={formik.touched.isaSenderId && Boolean(formik.errors.isaSenderId)}
              helperText={
                (formik.touched.isaSenderId && formik.errors.isaSenderId) ||
                'ISA segment sender identifier (max 15 chars)'
              }
              inputProps={{ maxLength: 15, style: { textTransform: 'uppercase' } }}
            />
          </Grid>

          <Grid item xs={12} md={4}>
            <TextField
              fullWidth
              id="gsApplicationSenderId"
              name="gsApplicationSenderId"
              label="GS Application Sender ID"
              value={formik.values.gsApplicationSenderId}
              onChange={formik.handleChange}
              onBlur={formik.handleBlur}
              error={formik.touched.gsApplicationSenderId && Boolean(formik.errors.gsApplicationSenderId)}
              helperText={
                (formik.touched.gsApplicationSenderId && formik.errors.gsApplicationSenderId) ||
                'GS segment application sender ID (max 15 chars)'
              }
              inputProps={{ maxLength: 15, style: { textTransform: 'uppercase' } }}
            />
          </Grid>
        </Grid>

        {/* Address Information */}
        <Divider sx={{ my: 3 }} />
        <Typography variant="h6" sx={{ mb: 2 }}>
          Address Information
        </Typography>

        <Grid container spacing={3}>
          <Grid item xs={12}>
            <TextField
              fullWidth
              id="addressStreet"
              name="addressStreet"
              label="Street Address"
              value={formik.values.addressStreet}
              onChange={formik.handleChange}
              onBlur={formik.handleBlur}
              error={formik.touched.addressStreet && Boolean(formik.errors.addressStreet)}
              helperText={formik.touched.addressStreet && formik.errors.addressStreet}
            />
          </Grid>

          <Grid item xs={12} md={4}>
            <TextField
              fullWidth
              id="addressCity"
              name="addressCity"
              label="City"
              value={formik.values.addressCity}
              onChange={formik.handleChange}
              onBlur={formik.handleBlur}
              error={formik.touched.addressCity && Boolean(formik.errors.addressCity)}
              helperText={formik.touched.addressCity && formik.errors.addressCity}
            />
          </Grid>

          <Grid item xs={12} md={4}>
            <TextField
              fullWidth
              id="addressState"
              name="addressState"
              label="State"
              value={formik.values.addressState}
              onChange={formik.handleChange}
              onBlur={formik.handleBlur}
              error={formik.touched.addressState && Boolean(formik.errors.addressState)}
              helperText={
                (formik.touched.addressState && formik.errors.addressState) ||
                'Two-letter state code (e.g., CA, NY)'
              }
              inputProps={{ maxLength: 2, style: { textTransform: 'uppercase' } }}
            />
          </Grid>

          <Grid item xs={12} md={4}>
            <TextField
              fullWidth
              id="addressZip"
              name="addressZip"
              label="ZIP Code"
              value={formik.values.addressZip}
              onChange={formik.handleChange}
              onBlur={formik.handleBlur}
              error={formik.touched.addressZip && Boolean(formik.errors.addressZip)}
              helperText={
                (formik.touched.addressZip && formik.errors.addressZip) ||
                'Format: 12345 or 12345-6789'
              }
              inputProps={{ maxLength: 10 }}
            />
          </Grid>
        </Grid>

        {/* SFTP Configuration */}
        <Divider sx={{ my: 3 }} />
        <Typography variant="h6" sx={{ mb: 2 }}>
          SFTP Configuration (Optional)
        </Typography>

        <Grid container spacing={3}>
          <Grid item xs={12} md={6}>
            <TextField
              fullWidth
              id="sftpHost"
              name="sftpHost"
              label="SFTP Host"
              value={formik.values.sftpHost}
              onChange={formik.handleChange}
              onBlur={formik.handleBlur}
              error={formik.touched.sftpHost && Boolean(formik.errors.sftpHost)}
              helperText={
                (formik.touched.sftpHost && formik.errors.sftpHost) ||
                'SFTP server hostname or IP address'
              }
            />
          </Grid>

          <Grid item xs={12} md={6}>
            <TextField
              fullWidth
              id="sftpPort"
              name="sftpPort"
              label="SFTP Port"
              type="number"
              value={formik.values.sftpPort || ''}
              onChange={formik.handleChange}
              onBlur={formik.handleBlur}
              error={formik.touched.sftpPort && Boolean(formik.errors.sftpPort)}
              helperText={
                (formik.touched.sftpPort && formik.errors.sftpPort) ||
                'SFTP port (default: 22)'
              }
            />
          </Grid>

          <Grid item xs={12} md={6}>
            <TextField
              fullWidth
              id="sftpUsername"
              name="sftpUsername"
              label="SFTP Username"
              value={formik.values.sftpUsername}
              onChange={formik.handleChange}
              onBlur={formik.handleBlur}
              error={formik.touched.sftpUsername && Boolean(formik.errors.sftpUsername)}
              helperText={formik.touched.sftpUsername && formik.errors.sftpUsername}
            />
          </Grid>

          <Grid item xs={12} md={6}>
            <TextField
              fullWidth
              id="sftpPassword"
              name="sftpPassword"
              label="SFTP Password"
              type="password"
              value={formik.values.sftpPassword}
              onChange={formik.handleChange}
              onBlur={formik.handleBlur}
              error={formik.touched.sftpPassword && Boolean(formik.errors.sftpPassword)}
              helperText={
                (formik.touched.sftpPassword && formik.errors.sftpPassword) ||
                'Password will be encrypted before storage'
              }
              placeholder={isEditMode ? '••••••••' : ''}
            />
          </Grid>

          <Grid item xs={12}>
            <TextField
              fullWidth
              id="sftpPath"
              name="sftpPath"
              label="SFTP Path"
              value={formik.values.sftpPath}
              onChange={formik.handleChange}
              onBlur={formik.handleBlur}
              error={formik.touched.sftpPath && Boolean(formik.errors.sftpPath)}
              helperText={
                (formik.touched.sftpPath && formik.errors.sftpPath) ||
                'Remote directory path for file delivery'
              }
            />
          </Grid>
        </Grid>

        {/* Status Switches */}
        <Divider sx={{ my: 3 }} />
        <Grid container spacing={3}>
          <Grid item xs={12} md={6}>
            <FormControlLabel
              control={
                <Switch
                  id="requiresSpecialHandling"
                  name="requiresSpecialHandling"
                  checked={formik.values.requiresSpecialHandling}
                  onChange={formik.handleChange}
                />
              }
              label="Requires Special Handling"
            />
          </Grid>

          <Grid item xs={12} md={6}>
            <FormControlLabel
              control={
                <Switch
                  id="isActive"
                  name="isActive"
                  checked={formik.values.isActive}
                  onChange={formik.handleChange}
                />
              }
              label="Active"
            />
          </Grid>
        </Grid>

        {/* Form Actions */}
        <Box sx={{ mt: 4, display: 'flex', gap: 2, justifyContent: 'flex-end' }}>
          {onCancel && (
            <Button variant="outlined" onClick={onCancel} disabled={loading}>
              Cancel
            </Button>
          )}
          <Button
            type="submit"
            variant="contained"
            color="primary"
            disabled={loading || !formik.isValid}
            startIcon={loading && <CircularProgress size={20} />}
          >
            {loading ? 'Saving...' : isEditMode ? 'Update Payer' : 'Create Payer'}
          </Button>
        </Box>
      </Box>
    </Paper>
  );
};

export default PayerForm;
