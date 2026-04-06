import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
import { ErrorResponse } from '../models/scan.model';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      let errorMessage = 'An unexpected error occurred.';

      if (error.error instanceof ErrorEvent) {
        errorMessage = `Network error: ${error.error.message}`;
      } else if (error.error && typeof error.error === 'object') {
        const serverError = error.error as ErrorResponse;
        errorMessage = serverError.message || errorMessage;

        if (serverError.fieldErrors && serverError.fieldErrors.length > 0) {
          const fieldMessages = serverError.fieldErrors
            .map(fe => `${fe.field}: ${fe.message}`)
            .join(', ');
          errorMessage = `${errorMessage} (${fieldMessages})`;
        }
      } else if (typeof error.error === 'string') {
        errorMessage = error.error;
      }

      switch (error.status) {
        case 0:
          errorMessage = 'Server is unavailable. Check your connection.';
          break;
        case 403:
          errorMessage = 'You do not have permission to access this resource.';
          break;
        case 404:
          errorMessage = error.error?.message || 'Resource not found.';
          break;
        case 413:
          errorMessage = 'File is too large (maximum 200MB).';
          break;
        case 422:
          errorMessage = error.error?.message || 'Scan processing failed.';
          break;
        case 503:
          errorMessage = 'AI service is currently unavailable.';
          break;
      }

      console.error(`[HTTP Error] ${error.status} - ${req.url}: ${errorMessage}`);

      return throwError(() => ({
        status: error.status,
        message: errorMessage,
        originalError: error
      }));
    })
  );
};