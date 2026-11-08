# CarPlay telephony and contacts

Telephone/car data is enabled by `Features.PHONE_DATA=true` and
`HOOK_PHONE_DATA=1`. Directory refresh uses Audi's stock address-book service.

## The gate

`GatedPhoneService` follows stock's AppleLink verdict (mobile connection type 5).
While CarPlay owns the telephone tab it presents HFP type 3 and supplies CarPlay
calls, recents, favourites, battery, network information and supported actions.
Stock updates for gated functions are retained and replayed after release.

The CarPlay presentation holds back the departed Bluetooth phone's SIM-lock state;
packet-data type remains the car modem's value. Incoming calls, dialing, active and
held calls retain the stock BAP contracts and completion responses.

## Calls and lists

| Path | Owner |
| --- | --- |
| Call state | stock DSICarplay callback → GatedPhoneService |
| Recents/favourites and communications | iAP2 hook → CarplayBus → telephone adapter |
| Accept/hangup/hold/swap/mute | GatedPhoneListener → iAP2 commands |
| Dial | CarPlay call-number path |
| Directory contacts | Audi address-book service, separate from iAP2 lists |

The first complete recents list in a session is the missed-call baseline; it does
not make every historical missed call new. Call data does not provide a caller
photo. Short-press Siri and the telephone-popup skip are separate build features.

## Directory refresh

Audi caches its last reported address-book state even without a consumer. Its
phonebook handler also has a list-size cache separate from PbState. A late bind or
unchanged contact count can therefore leave the cluster's Directory empty without
an explicit list notification.

`AddressBookRefresh` seeds a bound consumer with current stock readiness/count,
then queries the live stock phone view on binding and each CarPlay activation.
Its query forces `phonebookChanged` even for an unchanged count. Early activation
waits for Audi's real readiness callback. An empty live result stays empty.

The telephone adapter does not copy contact records from iAP2 or replay a previous phone's book.
List, details and speller responses continue through Audi's handlers. Diagnostics
record state/counts, not contact names or numbers. Collect logs before rollback if
a regression occurs; logs from other telephone paths can still contain personal data.

## Implementation and checks

- [Telephone adapters](../../java_patch/com/luka/carplay/telephony/)
- [Address-book refresh](../../java_patch/com/luka/carplay/telephony/AddressBookRefresh.java)
- [Pinned binding insert](../../tools/java/AddressBookPatch.java)
- [Native telephone hook](../../hook/telephony/tel_hook.c)
- [Directory regression](../../tests/GatedAddressBookTest.java)

The regression exercises the actual stock state/query/list handlers, including an
unchanged-count control that stays empty without refresh. Pinning and bytecode
preservation checks protect the stock service insertion. Local checks and the
operator's vehicle acceptance are distinct evidence.
