//Copyright (C)2014-2021 GOWIN Semiconductor Corporation.
//All rights reserved.
//File Title: Timing Constraints file
//GOWIN Version: 1.9.7.02 Beta
//Created Time: 2021-06-01 10:34:02
create_clock -name I_clk -period 37.037 -waveform {0 18.518} [get_ports {I_clk}] -add
//create_clock -name serial_clk -period 2.694 -waveform {0 1.347} [get_nets {gbHdmi_io_serClk}] -add
//create_clock -name pix_clk -period 13.468 -waveform {0 6.734} [get_nets {pix_clk}] -add
