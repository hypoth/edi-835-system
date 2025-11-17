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
import { CreatePayeeFromBucketRequest } from '../../types/models';

interface CreatePayeeFormProps {
  open: boolean;
  onClose: () => void;
  bucketId: string;
  payeeId: string;
  payeeName: string;
  onSubmit: (data: CreatePayeeFromBucketRequest) => Promise<any>;
  isSubmitting?: boolean;
}

const validationSchema = Yup.object({
  payeeId: Yup.string().required('Payee ID is required'),
  payeeName: Yup.string().required('Payee name is required'),
  npi: Yup.string().matches(/^\d{10}$/, 'NPI must be exactly 10 digits'),
  taxId: Yup.string().matches(/^\d{2}-?\d{7}$/, 'Tax ID must be in format XX-XXXXXXX or XXXXXXXXX'),
  addressStreet: Yup.string(),
  addressCity: Yup.string(),
  addressState: Yup.string().max(2, 'State should be 2 characters'),
  addressZip: Yup.string(),
});

const CreatePayeeForm: React.FC<CreatePayeeFormProps> = ({
  open,
  onClose,
  bucketId,
  payeeId,
  payeeName,
  onSubmit,
  isSubmitting = false,
}) => {
  const formik = useFormik<CreatePayeeFromBucketRequest>({
    initialValues: {
      bucketId,
      payeeId,
      payeeName,
      npi: '',
      taxId: '',
      addressStreet: '',
      addressCity: '',
      addressState: '',
      addressZip: '',
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
            Create Payee Configuration
          </Typography>
          <Typography variant="body2" color="textSecondary" sx={{ mt: 1 }}>
            Provide the provider identifiers and address information for payee: <strong>{payeeName}</strong>
          </Typography>
        </DialogTitle>

        <DialogContent dividers>
          <Box sx={{ mb: 3 }}>
            <Alert severity="info" sx={{ mb: 2 }}>
              NPI and Tax ID are recommended for EDI 835 file generation
            </Alert>

            {/* Basic Information */}
            <Typography variant="subtitle1" fontWeight={600} gutterBottom>
              Basic Information
            </Typography>
            <Grid container spacing={2} sx={{ mb: 3 }}>
              <Grid item xs={12} sm={6}>
                <TextField
                  fullWidth
                  id="payeeId"
                  name="payeeId"
                  label="Payee ID"
                  value={formik.values.payeeId}
                  onChange={formik.handleChange}
                  error={formik.touched.payeeId && Boolean(formik.errors.payeeId)}
                  helperText={formik.touched.payeeId && formik.errors.payeeId}
                  disabled
                  required
                />
              </Grid>
              <Grid item xs={12} sm={6}>
                <TextField
                  fullWidth
                  id="payeeName"
                  name="payeeName"
                  label="Payee Name"
                  value={formik.values.payeeName}
                  onChange={formik.handleChange}
                  error={formik.touched.payeeName && Boolean(formik.errors.payeeName)}
                  helperText={formik.touched.payeeName && formik.errors.payeeName}
                  required
                />
              </Grid>
            </Grid>

            {/* Provider Identifiers */}
            <Typography variant="subtitle1" fontWeight={600} gutterBottom>
              Provider Identifiers (Recommended)
            </Typography>
            <Grid container spacing={2} sx={{ mb: 3 }}>
              <Grid item xs={12} sm={6}>
                <TextField
                  fullWidth
                  id="npi"
                  name="npi"
                  label="NPI (National Provider Identifier)"
                  value={formik.values.npi}
                  onChange={formik.handleChange}
                  error={formik.touched.npi && Boolean(formik.errors.npi)}
                  helperText={formik.touched.npi && formik.errors.npi || '10-digit number'}
                  inputProps={{ maxLength: 10 }}
                  placeholder="1234567890"
                />
              </Grid>
              <Grid item xs={12} sm={6}>
                <TextField
                  fullWidth
                  id="taxId"
                  name="taxId"
                  label="Tax ID (EIN)"
                  value={formik.values.taxId}
                  onChange={formik.handleChange}
                  error={formik.touched.taxId && Boolean(formik.errors.taxId)}
                  helperText={formik.touched.taxId && formik.errors.taxId || 'Format: XX-XXXXXXX'}
                  placeholder="12-3456789"
                />
              </Grid>
            </Grid>

            {/* Address Information */}
            <Typography variant="subtitle1" fontWeight={600} gutterBottom>
              Address Information (Optional)
            </Typography>
            <Grid container spacing={2}>
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
                  placeholder="FL"
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
                  placeholder="33602"
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
            {isSubmitting ? 'Creating...' : 'Create Payee'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
};

export default CreatePayeeForm;
