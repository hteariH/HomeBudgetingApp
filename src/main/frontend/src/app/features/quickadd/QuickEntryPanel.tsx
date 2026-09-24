import React from 'react';
import { EntryForm } from './EntryForm';

/**
 * The entry form in its compact shape, for the overview and the ledger on a wide window.
 * Saving clears it and leaves it where it is; the new row appears in the list beside it.
 */
export const QuickEntryPanel: React.FC = () => <EntryForm variant="panel" />;
