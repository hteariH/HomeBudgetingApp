import { fireEvent, render, screen } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import { I18nProvider } from '../../../i18n';
import { appEn } from '../../../i18n/locales/app.en';
import { ScanButton } from './ScanButton';

const renderButton = (reading: boolean) => {
    const onFile = vi.fn();
    const onCancel = vi.fn();
    render(
        <I18nProvider>
            <ScanButton reading={reading} onFile={onFile} onCancel={onCancel} />
        </I18nProvider>,
    );
    return { onFile, onCancel };
};

it('hands the picked photo over', () => {
    const { onFile } = renderButton(false);
    const file = new File(['x'], 'r.jpg', { type: 'image/jpeg' });
    fireEvent.change(screen.getByTestId('receipt-file'), { target: { files: [file] } });
    expect(onFile).toHaveBeenCalledWith(file);
    expect(screen.getByText(appEn.quickadd_scan_receipt)).toBeInTheDocument();
});

it('shows progress and a way to cancel while reading', () => {
    const { onCancel } = renderButton(true);
    expect(screen.getByText(appEn.receipt_reading)).toBeInTheDocument();
    fireEvent.click(screen.getByText(appEn.receipt_cancel));
    expect(onCancel).toHaveBeenCalled();
});
