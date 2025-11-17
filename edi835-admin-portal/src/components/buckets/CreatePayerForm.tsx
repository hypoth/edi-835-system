import React from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  Grid,
  Typography,
  Box,
  Alert,
  CircularProgress,
} from '@mui/material';
import { useFormik } from 'formik';
import * as Yup from 'yup';
import { CreatePayerFromBucketRequest } from '../../types/models';

interface CreatePayerFormProps {
  open: boolean;
  onClose: () => void;
  bucketId: string;
  payerId: string;
  payerName: string;
  onSubmit: (data: CreatePayerFromBucketRequest) => Promise<any>;
  isSubmitting?: boolean;
}

const validationSchema = Yup.object({
  payerId: Yup.string().required('Payer ID is required'),
  payerName: Yup.string().required('Payer name is required'),
  isaSenderId: Yup.string().required('ISA Sender ID is required'),
  isaQualifier: Yup.string(),
  gsApplicationSenderId: Yup.string(),
  addressStreet: Yup.string(),
  addressCity: Yup.string(),
  addressState: Yup.string().max(2, 'State should be 2 characters'),
  addressZip: Yup.string(),
  sftpHost: Yup.string(),
  sftpPort: Yup.number().min(1).max(65535),
  sftpUsername: Yup.string(),
  sftpPassword: Yup.string(),
  sftpPath: Yup.string(),
});

const CreatePayerForm: React.FC<CreatePayerFormProps> = ({
  open,
  onClose,
  bucketId,
  payerId,
  payerName,
  onSubmit,
  isSubmitting = false,
}) => {
  const formik = useFormik<CreatePayerFromBucketRequest>({
    initialValues: {
      bucketId,
      payerId,
      payerName,
      isaSenderId: '',
      isaQualifier: 'ZZ',
      gsApplicationSenderId: '',
      addressStreet: '',
      addressCity: '',
      addressState: '',
      addressZip: '',
      sftpHost: '',
      sftpPort: 22,
      sftpUsername: '',
      sftpPassword: '',
      sftpPath: '',
      requiresSpecialHandling: false,
      isActive: true,
      createdBy: 'admin', // TODO: Get from auth context
    },
    validationSchema,
    onSubmit: async (values) => {
      await onSubmit(values);
    },
    enableReinitialize: true,
  });

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <form onSubmit={formik.handleSubmit}>
        <DialogTitle>
          <Typography variant="h6" fontWeight={600}>
            Create Payer Configuration
          </Typography>
          <Typography variant="body2" color="textSecondary" sx={{ mt: 1 }}>
            Provide the required EDI identifiers and optional address/SFTP information for payer: <strong>{payerName}</strong>
          </Typography>
        </DialogTitle>

        <DialogContent dividers>
          <Box sx={{ mb: 3 }}>
            <Alert severity="info" sx={{ mb: 2 }}>
              Fields marked with * are required for EDI 835 file generation
            </Alert>

            {/* Basic Information */}
            <Typography variant="subtitle1" fontWeight={600} gutterBottom>
              Basic Information
            </Typography>
            <Grid container spacing={2} sx={{ mb: 3 }}>
              <Grid item xs={12} sm={6}>
                <TextField
                  fullWidth
                  id="payerId"
                  name="payerId"
                  label="Payer ID"
                  value={formik.values.payerId}
                  onChange={formik.handleChange}
                  error={formik.touched.payerId && Boolean(formik.errors.payerId)}
                  helperText={formik.touched.payerId && formik.errors.payerId}
                  disabled
                  required
                />
              </Grid>
              <Grid item xs={12} sm={6}>
                <TextField
                  fullWidth
                  id="payerName"
                  name="payerName"
                  label="Payer Name"
                  value={formik.values.payerName}
                  onChange={formik.handleChange}
                  error={formik.touched.payerName && Boolean(formik.errors.payerName)}
                  helperText={formik.touched.payerName && formik.errors.payerName}
                  required
                />
              </Grid>
            </Grid>

            {/* EDI Identifiers */}
            <Typography variant="subtitle1" fontWeight={600} gutterBottom>
              EDI Identifiers *
            </Typography>
            <Grid container spacing={2} sx={{ mb: 3 }}>
              <Grid item xs={12} sm={6}>
                <TextField
                  fullWidth
                  id="isaSenderId"
                  name="isaSenderId"
                  label="ISA Sender ID"
                  value={formik.values.isaSenderId}
                  onChange={formik.handleChange}
                  error={formik.touched.isaSenderId && Boolean(formik.errors.isaSenderId)}
                  helperText={formik.touched.isaSenderId && formik.errors.isaSenderId || 'Used in ISA segment'}
                  required
                />
              </Grid>
              <Grid item xs={12} sm={6}>
                <TextField
                  fullWidth
                  id="isaQualifier"
                  name="isaQualifier"
                  label="ISA Qualifier"
                  value={formik.values.isaQualifier}
                  onChange={formik.handleChange}
                  error={formik.touched.isaQualifier && Boolean(formik.errors.isaQualifier)}
                  helperText="Default: ZZ"
                />
              </Grid>
              <Grid item xs={12}>
                <TextField
                  fullWidth
                  id="gsApplicationSenderId"
                  name="gsApplicationSenderId"
                  label="GS Application Sender ID (Optional)"
                  value={formik.values.gsApplicationSenderId}
                  onChange={formik.handleChange}
                  error={formik.touched.gsApplicationSenderId && Boolean(formik.errors.gsApplicationSenderId)}
                  helperText="Used in GS segment"
                />
              </Grid>
            </Grid>

            {/* Address Information */}
            <Typography variant="subtitle1" fontWeight={600} gutterBottom>
              Address Information (Optional)
            </Typography>
            <Grid container spacing={2} sx={{ mb: 3 }}>
              <Grid item xs={12}>
                <TextField
                  fullWidth
                  id="addressStreet"
                  name="addressStreet"
                  label="Street Address"
                  value={formik.values.addressStreet}
                  onChange={formik.handleChange}
                />
              </Grid>
              <Grid item xs={12} sm={5}>
                <TextField
                  fullWidth
                  id="addressCity"
                  name="addressCity"
                  label="City"
                  value={formik.values.addressCity}
                  onChange={formik.handleChange}
                />
              </Grid>
              <Grid item xs={12} sm={3}>
                <TextField
                  fullWidth
                  id="addressState"
                  name="addressState"
                  label="State"
                  value={formik.values.addressState}
                  onChange={formik.handleChange}
                  error={formik.touched.addressState && Boolean(formik.errors.addressState)}
                  helperText={formik.touched.addressState && formik.errors.addressState}
                  inputProps={{ maxLength: 2 }}
                />
              </Grid>
              <Grid item xs={12} sm={4}>
                <TextField
                  fullWidth
                  id="addressZip"
                  name="addressZip"
                  label="ZIP Code"
                  value={formik.values.addressZip}
                  onChange={formik.handleChange}
                />
              </Grid>
            </Grid>

            {/* SFTP Configuration */}
            <Typography variant="subtitle1" fontWeight={600} gutterBottom>
              SFTP Configuration (Optional)
            </Typography>
            <Grid container spacing={2}>
              <Grid item xs={12} sm={8}>
                <TextField
                  fullWidth
                  id="sftpHost"
                  name="sftpHost"
                  label="SFTP Host"
                  value={formik.values.sftpHost}
                  onChange={formik.handleChange}
                  placeholder="sftp.payer.com"
                />
              </Grid>
              <Grid item xs={12} sm={4}>
                <TextField
                  fullWidth
                  type="number"
                  id="sftpPort"
                  name="sftpPort"
                  label="SFTP Port"
                  value={formik.values.sftpPort}
                  onChange={formik.handleChange}
                  error={formik.touched.sftpPort && Boolean(formik.errors.sftpPort)}
                  helperText={formik.touched.sftpPort && formik.errors.sftpPort}
                />
              </Grid>
              <Grid item xs={12} sm={6}>
                <TextField
                  fullWidth
                  id="sftpUsername"
                  name="sftpUsername"
                  label="SFTP Username"
                  value={formik.values.sftpUsername}
                  onChange={formik.handleChange}
                />
              </Grid>
              <Grid item xs={12} sm={6}>
                <TextField
                  fullWidth
                  type="password"
                  id="sftpPassword"
                  name="sftpPassword"
                  label="SFTP Password"
                  value={formik.values.sftpPassword}
                  onChange={formik.handleChange}
                />
              </Grid>
              <Grid item xs={12}>
                <TextField
                  fullWidth
                  id="sftpPath"
                  name="sftpPath"
                  label="SFTP Remote Path"
                  value={formik.values.sftpPath}
                  onChange={formik.handleChange}
                  placeholder="/edi/835"
                />
              </Grid>
            </Grid>
          </Box>
        </DialogContent>

        <DialogActions>
          <Button onClick={onClose} disabled={isSubmitting}>
            Cancel
          </Button>
          <Button
            type="submit"
            variant="contained"
            disabled={isSubmitting || !formik.isValid}
            startIcon={isSubmitting ? <CircularProgress size={20} /> : null}
          >
            {isSubmitting ? 'Creating...' : 'Create Payer'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
};

export default CreatePayerForm;
