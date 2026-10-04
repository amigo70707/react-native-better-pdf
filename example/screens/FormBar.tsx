import { useState } from 'react';
import { StyleSheet, View } from 'react-native';
import type { PdfFormField, PdfViewRef } from 'react-native-better-pdf';
import { Button } from '../ui';

/**
 * Form filling: "Fill" sets values from code, "Fields" lists the current
 * values on the event line, "Bad" tries a read-only field, "Touch" turns
 * filling by touch on and off, "Export" writes the filled-in document and
 * "Open" shows that copy.
 */
export function FormBar({
  pdf,
  isFillingEnabled,
  onToggleFilling,
  onEvent,
  onOpen,
}: {
  pdf: PdfViewRef | null;
  isFillingEnabled: boolean;
  onToggleFilling: () => void;
  onEvent: (text: string) => void;
  onOpen: (filePath: string) => void;
}) {
  const [exported, setExported] = useState<string | null>(null);
  const fail = (what: string) => (error: unknown) =>
    // Android's rejections carry the Java stack trace: first line only.
    onEvent(`${what}: ${String(error).split('\n')[0]}`);
  return (
    <View style={styles.row}>
      <Button
        title="Fill"
        onPress={() => {
          pdf
            ?.setFormFieldValues({
              name: 'Ewa',
              notes: 'First line\nSecond line',
              agree: true,
              plan: 'pro',
              country: 'France',
              colours: ['Blue'],
              'address.city': 'Kraków',
            })
            .then(() => onEvent('fill: done'))
            .catch(fail('fill'));
        }}
      />
      <Button
        title="Fields"
        onPress={() => {
          pdf
            ?.getFormFields()
            .then((fields) =>
              onEvent(
                `fields ${fields.length}: ${fields.map(describeField).join(' ')}`,
              ),
            )
            .catch(fail('fields'));
        }}
      />
      <Button
        title="Bad"
        onPress={() => {
          pdf
            ?.setFormFieldValues({ name: 'Never', id: 'B-1' })
            .then(() => onEvent('bad: accepted'))
            .catch(fail('bad'));
        }}
      />
      <Button
        title={isFillingEnabled ? 'Touch on' : 'Touch off'}
        onPress={onToggleFilling}
      />
      <Button
        title="Export"
        onPress={() => {
          pdf
            ?.exportDocument()
            .then((result) => {
              setExported(result.filePath);
              onEvent(`export: ${result.filePath.split('/').pop()}`);
            })
            .catch(fail('export'));
        }}
      />
      <Button
        title="Open"
        onPress={() => {
          if (exported != null) onOpen(exported);
        }}
      />
    </View>
  );
}

/** `name=value`, for the event line. */
export function describeField(field: PdfFormField): string {
  const value = (() => {
    switch (field.type) {
      case 'text':
        return field.value.replace(/\n/g, '/');
      case 'checkbox':
        return field.isChecked ? 'on' : 'off';
      case 'radio':
      case 'combo-box':
        return field.value ?? '-';
      case 'list-box':
        return field.values.join('+') || '-';
      case 'signature':
        return field.isSigned ? 'signed' : 'unsigned';
      case 'push-button':
        return 'button';
    }
  })();
  return `${field.name}=${value}`;
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    alignItems: 'center',
    paddingHorizontal: 8,
  },
});
