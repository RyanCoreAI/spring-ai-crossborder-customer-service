import { describe, expect, it } from "vitest";
import {
  adminNavigation,
  canViewAdminPage,
  findAdminPage,
  type AdminNavItem,
} from "./adminNavigation";

function page(path: string) {
  return adminNavigation
    .flatMap((group) => group.children || [])
    .find((item) => item.path === path) as AdminNavItem;
}

describe("admin navigation visibility", () => {
  it("lets platform administrators access every registered page", () => {
    const pages = adminNavigation.flatMap((group) => group.children || []);
    expect(pages.every((item) => canViewAdminPage(item, [], true))).toBe(true);
  });

  it("keeps tenant and identity administration away from tenant roles", () => {
    expect(canViewAdminPage(page("/admin/tenants"), ["TENANT_ADMIN"], false)).toBe(false);
    expect(canViewAdminPage(page("/admin/users"), ["TENANT_ADMIN"], false)).toBe(false);
  });

  it("shows operational work to support agents but not approval pages", () => {
    expect(canViewAdminPage(page("/admin/inbox"), ["SUPPORT_AGENT"], false)).toBe(true);
    expect(canViewAdminPage(page("/admin/actions"), ["SUPPORT_AGENT"], false)).toBe(false);
    expect(canViewAdminPage(page("/admin/integrations"), ["SUPPORT_AGENT"], false)).toBe(false);
  });

  it("gives read-only auditors assurance surfaces without channel management", () => {
    expect(canViewAdminPage(page("/admin/observability"), ["READ_ONLY_AUDITOR"], false)).toBe(true);
    expect(canViewAdminPage(page("/admin/audit"), ["READ_ONLY_AUDITOR"], false)).toBe(true);
    expect(canViewAdminPage(page("/admin/channels"), ["READ_ONLY_AUDITOR"], false)).toBe(false);
  });

  it("resolves nested admin URLs through the same permission definition", () => {
    expect(findAdminPage("/admin/customers/42")?.path).toBe("/admin/customers");
    expect(findAdminPage("/admin/users")?.platformAdminOnly).toBe(true);
  });
});
