package dev.infectedware.skysong.module;

import java.math.BigDecimal;
import java.math.RoundingMode;
import net.minecraft.util.Mth;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public sealed interface Setting
    permits Setting.Header,
    Setting.Choice,
    Setting.Multi,
    Setting.Slide,
    Setting.Toggle,
    Setting.Action,
    Setting.Info,
    Setting.ColorPick,
    Setting.Input,
    Setting.KeyPick,
    Setting.ListRow,
    Setting.Shown {
    static Setting unwrap(Setting s) {
        while (s instanceof Setting.Shown shown) {
            s = shown.setting();
        }

        return s;
    }

    static boolean visible(Setting s) {
        while (s instanceof Setting.Shown(BooleanSupplier when, Setting inner)) {
            if (!when.getAsBoolean()) {
                return false;
            }

            s = inner;
        }

        return true;
    }

    record Action(String label, Runnable run) implements Setting {
    }

    record Choice(String label, String[] options, IntSupplier get, IntConsumer set) implements Setting {
    }

    record ColorPick(String label, IntSupplier get, IntConsumer set) implements Setting {
    }

    record Header(String title) implements Setting {
    }

    record Info(Supplier<String> text) implements Setting {
    }

    record Input(String placeholder, String button, Consumer<String> submit) implements Setting {
    }

    record KeyPick(String label, IntSupplier get, IntConsumer set) implements Setting {
    }

    record ListRow(String label, BooleanSupplier active, Runnable click, Runnable remove) implements Setting {
    }

    record Multi(String label, String[] options, IntSupplier mask, IntConsumer set) implements Setting {
    }

    record Shown(BooleanSupplier when, Setting setting) implements Setting {
    }

    record Slide(String label, float min, float max, float step, double resetTo, DoubleSupplier get, DoubleConsumer set) implements Setting {

        public float fraction() {
            return (float)Math.clamp((this.get.getAsDouble() - this.min) / (this.max - this.min), 0.0, 1.0);
        }

        public void setFraction(float f) {
            double v = this.min + Math.clamp(f, 0.0F, 1.0F) * (this.max - this.min);
            if (this.step > 0.0F) {
                v = (float)Math.round(v / this.step) * this.step;
            }

            this.set.accept(Mth.clamp(v, this.min, this.max));
        }

        public String text() {
            return this.format(this.get.getAsDouble());
        }

        public String maxText() {
            return this.format(this.max);
        }

        private String format(double v) {
            if (this.step >= 1.0F) {
                return String.format(Locale.ROOT, "%.0f", v);
            }

            int decimals = 1;
            if (this.step <= 0.0F) {
                decimals = 2;
            } else {
                while (decimals < 3 && Math.abs(this.step * Math.pow(10.0, decimals) - Math.round(this.step * Math.pow(10.0, decimals))) > 1.0E-4) {
                    decimals++;
                }
            }

            return new BigDecimal(v).setScale(decimals, RoundingMode.HALF_UP).toPlainString();
        }
    }

    record Toggle(String label, BooleanSupplier get, Consumer<Boolean> set) implements Setting {
    }
}
