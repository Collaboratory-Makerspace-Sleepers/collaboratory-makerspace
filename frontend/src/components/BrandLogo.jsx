import { Link } from "react-router-dom";

export default function BrandLogo() {
  return (
    <Link className="brand-logo" to="/" aria-label="The Collaboratory home">
      <img src="/collaboratory-logo.svg" alt="The Collaboratory" />
    </Link>
  );
}
