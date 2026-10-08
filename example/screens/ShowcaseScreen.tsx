import { useRef, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import {
  PdfPageOverlay,
  PdfView,
  type PdfFormFieldValue,
  type PdfSource,
  type PdfViewRef,
} from 'react-native-better-pdf';

export type ShowcaseFeature =
  'search' | 'forms' | 'overlays' | 'dark' | 'print';

export const SHOWCASE_FEATURES: readonly ShowcaseFeature[] = [
  'search',
  'forms',
  'overlays',
  'dark',
  'print',
];

const sleep = (ms: number) =>
  new Promise<void>((resolve) => setTimeout(resolve, ms));

/* eslint-disable @typescript-eslint/no-require-imports -- Metro assets */
const AGREEMENT = require('../assets/agreement.pdf') as number;
const KICKOFF_FORM = require('../assets/kickoff-form.pdf') as number;
/* eslint-enable @typescript-eslint/no-require-imports */

const SIGNATURE_PAGE = 4;

function sourceFor(feature: ShowcaseFeature): PdfSource {
  return feature === 'forms' ? KICKOFF_FORM : AGREEMENT;
}

const INITIAL_SCALE: Record<ShowcaseFeature, number> = {
  search: 1,
  forms: 1,
  overlays: 1,
  dark: 1,
  print: 1,
};

/**
 * A full-screen viewer that plays one feature through the library's real API
 * on load, for screen recordings of the features
 * (`betterpdfexample://showcase?feature=search`).
 */
export function ShowcaseScreen({ feature }: { feature: ShowcaseFeature }) {
  const pdf = useRef<PdfViewRef>(null);
  const [isDark, setDark] = useState(false);

  const play = async () => {
    const view = () => pdf.current;
    // Types a value into a form field one character at a time.
    const type = async (name: string, value: string) => {
      for (let length = 1; length <= value.length; length++) {
        await view()?.setFormFieldValues({ [name]: value.slice(0, length) });
        await sleep(28);
      }
    };
    await sleep(900);
    switch (feature) {
      case 'search':
        view()?.search('payment');
        for (let step = 0; step < 6; step++) {
          await sleep(650);
          view()?.searchNext();
        }
        break;
      case 'forms': {
        await type('name', 'Anna Kowalska');
        await type('company', 'Acme Inc.');
        await type('email', 'anna@acme.com');
        // The radio's options are whatever the document names them; pick the second (Pro).
        const fields = (await view()?.getFormFields()) ?? [];
        const plan = fields.find((field) => field.name === 'plan');
        const steps: Record<string, PdfFormFieldValue>[] = [
          { ios: true },
          { android: true },
          ...(plan?.type === 'radio' && plan.options[1] != null
            ? [{ plan: plan.options[1] }]
            : []),
          { country: 'Germany' },
        ];
        for (const values of steps) {
          await view()?.setFormFieldValues(values);
          await sleep(220);
        }
        await type('launch', '1 March 2027');
        await view()?.setFormFieldValues({ nda: true });
        break;
      }
      case 'overlays':
        view()?.zoomTo(1.6);
        await sleep(1500);
        view()?.zoomTo(1);
        await sleep(1100);
        view()?.goToPage(0);
        break;
      case 'print':
        await view()?.print({ jobName: 'Software Development Agreement' });
        break;
      case 'dark':
        for (let step = 0; step < 3; step++) {
          setDark((dark) => !dark);
          await sleep(1300);
        }
        break;
    }
  };

  return (
    <View style={[styles.fill, isDark && styles.dark]}>
      <PdfView
        ref={pdf}
        source={sourceFor(feature)}
        style={styles.fill}
        initialPage={feature === 'overlays' ? SIGNATURE_PAGE : 0}
        initialScale={INITIAL_SCALE[feature]}
        colorScheme={isDark ? 'dark' : 'light'}
        onLoad={() => void play()}
      >
        {feature === 'overlays' && <SignatureOverlays />}
      </PdfView>
    </View>
  );
}

/** React views placed on the agreement's pages (PDF points, A5 page). */
function SignatureOverlays() {
  return (
    <>
      <PdfPageOverlay page={0} x={300} y={64} width={80} height={22}>
        <View style={styles.badge}>
          <Text style={styles.badgeText}>Reviewed</Text>
        </View>
      </PdfPageOverlay>
      <PdfPageOverlay
        page={SIGNATURE_PAGE}
        x={186}
        y={286}
        width={150}
        height={42}
      >
        <View style={styles.comment}>
          <View style={styles.avatar}>
            <Text style={styles.avatarText}>A</Text>
          </View>
          <View style={styles.commentBody}>
            <Text style={styles.commentName}>Anna, Acme</Text>
            <Text style={styles.commentText}>Approved by legal</Text>
          </View>
        </View>
      </PdfPageOverlay>
      <PdfPageOverlay
        page={SIGNATURE_PAGE}
        x={96}
        y={362}
        width={118}
        height={30}
      >
        <View style={styles.flag}>
          <Text style={styles.flagText}>Sign here</Text>
          <View style={styles.flagTip} />
        </View>
      </PdfPageOverlay>
      <PdfPageOverlay
        page={SIGNATURE_PAGE}
        x={244}
        y={424}
        width={86}
        height={32}
      >
        <View style={styles.initials}>
          <Text style={styles.initialsText}>Initials</Text>
        </View>
      </PdfPageOverlay>
    </>
  );
}

const BLUE = '#0F52FF';

const styles = StyleSheet.create({
  fill: { flex: 1, backgroundColor: '#E4E4E7' },
  dark: { backgroundColor: '#27272A' },
  badge: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 11,
    backgroundColor: '#12B76A',
  },
  badgeText: { color: '#FFFFFF', fontWeight: '700', fontSize: 10 },
  comment: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 8,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: '#E3E6EB',
    backgroundColor: '#FFFFFF',
  },
  avatar: {
    width: 26,
    height: 26,
    borderRadius: 13,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: BLUE,
  },
  avatarText: { color: '#FFFFFF', fontWeight: '700', fontSize: 13 },
  commentBody: { marginLeft: 8 },
  commentName: { color: '#6B7280', fontSize: 9, fontWeight: '600' },
  commentText: { color: '#1B1F27', fontSize: 12, fontWeight: '600' },
  flag: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 15,
    backgroundColor: BLUE,
  },
  flagText: { color: '#FFFFFF', fontWeight: '700', fontSize: 13 },
  flagTip: {
    position: 'absolute',
    left: 18,
    bottom: -6,
    width: 12,
    height: 12,
    backgroundColor: BLUE,
    transform: [{ rotate: '45deg' }],
  },
  initials: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 6,
    borderWidth: 1.5,
    borderStyle: 'dashed',
    borderColor: BLUE,
    backgroundColor: 'rgba(15, 82, 255, 0.08)',
  },
  initialsText: { color: BLUE, fontWeight: '700', fontSize: 11 },
});
