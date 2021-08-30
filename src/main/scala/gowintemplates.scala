package gbhdmi

import chisel3._
import chisel3.util._
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}

/* IP is encrypted in src/verilog/dvi_tx.v */
class DVI_TX_Top extends BlackBox {
    val io = IO(new Bundle {
        val I_rst_n = Input(Bool())
        val I_serial_clk = Input(Clock())
        val I_rgb_clk = Input(Clock())
        val I_rgb_vs = Input(Bool())
        val I_rgb_hs = Input(Bool())
        val I_rgb_de = Input(Bool())
        val I_rgb_r = Input(UInt(8.W))
        val I_rgb_g = Input(UInt(8.W))
        val I_rgb_b = Input(UInt(8.W))
        val O_tmds_data_p  = Output(UInt(3.W))
        val O_tmds_data_n  = Output(UInt(3.W))
        val O_tmds_clk_p = Output(Bool())
        val O_tmds_clk_n = Output(Bool())
    })
}

/* clk div */
class CLKDIV extends BlackBox(Map("DIV_MODE" -> "5")) {
    val io = IO(new Bundle{
        val RESETN = Input(Bool())
        val HCLKIN = Input(Clock())
        val CLKOUT = Output(Clock())
        val CALIB = Input(Bool())
    })
}

/* TMDS PLLVR */
class TMDS_PLLVR extends BlackBox {
    val io = IO(new Bundle {
        val clkin = Input(Clock())
        val clkout = Output(Clock())
        val clkoutd = Output(Clock())
        val lock = Output(Bool())
    })
}
