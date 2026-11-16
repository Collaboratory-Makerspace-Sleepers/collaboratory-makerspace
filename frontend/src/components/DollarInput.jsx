import { dollarsToCents, formatDollars } from "../utils/catalog";

export default function DollarInput({ value, onChange, ...props }) {
  return (
    <input
      {...props}
      type="text"
      inputMode="decimal"
      required
      value={value}
      onChange={(event) => {
        event.target.setCustomValidity("");
        onChange(event.target.value);
      }}
      onBlur={(event) => {
        try {
          onChange(formatDollars(dollarsToCents(event.target.value)));
          event.target.setCustomValidity("");
        } catch (error) {
          event.target.setCustomValidity(error.message);
          event.target.reportValidity();
        }
      }}
    />
  );
}
