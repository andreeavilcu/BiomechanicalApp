import { TestBed } from '@angular/core/testing';
import { HttpInterceptorFn, HttpErrorResponse, HttpRequest, HttpHandlerFn, HttpResponse } from '@angular/common/http';
import { throwError, of, firstValueFrom } from 'rxjs';
import { errorInterceptor } from './error.interceptor';

const executeInterceptor: HttpInterceptorFn = (req, next) =>
  TestBed.runInInjectionContext(() => errorInterceptor(req, next));

const makeReq = () => new HttpRequest('GET', '/api/test');

const makeErrorNext = (status: number, errorBody: unknown = null): HttpHandlerFn =>
  () => throwError(() => new HttpErrorResponse({ status, error: errorBody, url: '/api/test' }));

describe('errorInterceptor', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({});
  });

  it('should be created', () => {
    expect(executeInterceptor).toBeTruthy();
  });

  it('maps status 0 to "Server is unavailable" message', async () => {
    await expect(firstValueFrom(executeInterceptor(makeReq(), makeErrorNext(0)))).rejects.toMatchObject({
      status: 0,
      message: 'Server is unavailable. Check your connection.',
    });
  });

  it('maps status 403 to permission denied message', async () => {
    await expect(firstValueFrom(executeInterceptor(makeReq(), makeErrorNext(403)))).rejects.toMatchObject({
      status: 403,
      message: 'You do not have permission to access this resource.',
    });
  });

  it('maps status 404 to "Resource not found." when no body message', async () => {
    await expect(firstValueFrom(executeInterceptor(makeReq(), makeErrorNext(404, null)))).rejects.toMatchObject({
      status: 404,
      message: 'Resource not found.',
    });
  });

  it('maps status 404 to server message when body has message', async () => {
    await expect(
      firstValueFrom(executeInterceptor(makeReq(), makeErrorNext(404, { message: 'User not found' })))
    ).rejects.toMatchObject({ status: 404, message: 'User not found' });
  });

  it('maps status 413 to file too large message', async () => {
    await expect(firstValueFrom(executeInterceptor(makeReq(), makeErrorNext(413)))).rejects.toMatchObject({
      status: 413,
      message: 'File is too large (maximum 200MB).',
    });
  });

  it('maps status 422 to "Scan processing failed." when no body message', async () => {
    await expect(firstValueFrom(executeInterceptor(makeReq(), makeErrorNext(422, null)))).rejects.toMatchObject({
      status: 422,
      message: 'Scan processing failed.',
    });
  });

  it('maps status 422 to server message when body has message', async () => {
    await expect(
      firstValueFrom(executeInterceptor(makeReq(), makeErrorNext(422, { message: 'Invalid scan data' })))
    ).rejects.toMatchObject({ status: 422, message: 'Invalid scan data' });
  });

  it('maps status 503 to AI service unavailable message', async () => {
    await expect(firstValueFrom(executeInterceptor(makeReq(), makeErrorNext(503)))).rejects.toMatchObject({
      status: 503,
      message: 'AI service is currently unavailable.',
    });
  });

  it('includes originalError in the thrown object', async () => {
    await expect(firstValueFrom(executeInterceptor(makeReq(), makeErrorNext(500)))).rejects.toMatchObject({
      originalError: expect.any(HttpErrorResponse),
    });
  });

  it('passes through successful responses unchanged', async () => {
    const successNext: HttpHandlerFn = () => of(new HttpResponse({ status: 200, body: 'ok' }));
    const result = await firstValueFrom(executeInterceptor(makeReq(), successNext)) as HttpResponse<string>;
    expect(result.body).toBe('ok');
  });
});
